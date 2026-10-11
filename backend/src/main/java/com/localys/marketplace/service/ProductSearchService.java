package com.localys.marketplace.service;

import com.localys.marketplace.model.Category;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.enums.ItemCondition;
import com.localys.marketplace.model.enums.ModerationStatus;
import com.localys.marketplace.repository.CategoryRepository;
import com.localys.marketplace.repository.ProductRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Public listing search: filtering, sorting and paging happen in the database so the
 * browser never has to download the whole catalogue.
 */
@Service
public class ProductSearchService {

    public static final int DEFAULT_PAGE_SIZE = 24;
    private static final int MAX_PAGE_SIZE = 60;
    private static final int MAX_SEARCH_TERMS = 5;
    private static final double EARTH_RADIUS_KM = 6371.0;

    public enum SortOption {
        NEWEST(Sort.by(Sort.Order.desc("listedAt"), Sort.Order.desc("id"))),
        PRICE_ASC(Sort.by(Sort.Order.asc("price"), Sort.Order.desc("id"))),
        PRICE_DESC(Sort.by(Sort.Order.desc("price"), Sort.Order.desc("id"))),
        NAME_ASC(Sort.by(Sort.Order.asc("name"), Sort.Order.desc("id")));

        private final Sort sort;

        SortOption(Sort sort) {
            this.sort = sort;
        }

        static SortOption from(String raw) {
            if (raw == null || raw.isBlank()) {
                return NEWEST;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return NEWEST;
            }
        }
    }

    /** How the buyer wants to receive the item; null means either. */
    public enum Delivery {
        HAND,
        SHIPPING;

        public static Delivery from(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }

    public record Criteria(
            String query,
            Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String sort,
            Integer page,
            Integer size,
            /** Only this user's listings (public profile); null for everyone's. */
            Long sellerUserId,
            /** Any of these conditions; empty for all. */
            List<ItemCondition> conditions,
            Delivery delivery,
            /** Only listings within this area; null for everywhere. */
            Area area
    ) {
    }

    /** A circle around a point, e.g. the buyer's town. */
    public record Area(double latitude, double longitude, double radiusKm) {
        public static final double MAX_RADIUS_KM = 200;

        /** Null unless a valid point is given; the radius is clamped to 1..200 km (default 10). */
        public static Area of(Double latitude, Double longitude, Double radiusKm) {
            if (latitude == null || longitude == null
                    || Math.abs(latitude) > 90 || Math.abs(longitude) > 180
                    || latitude.isNaN() || longitude.isNaN()) {
                return null;
            }
            double radius = radiusKm == null || radiusKm.isNaN() ? 10 : Math.min(Math.max(radiusKm, 1), MAX_RADIUS_KM);
            return new Area(latitude, longitude, radius);
        }
    }

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductSearchService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    public Page<Product> search(Criteria criteria) {
        int page = criteria.page() == null ? 0 : Math.max(0, criteria.page());
        int size = criteria.size() == null
                ? DEFAULT_PAGE_SIZE
                : Math.min(Math.max(1, criteria.size()), MAX_PAGE_SIZE);
        SortOption sortOption = SortOption.from(criteria.sort());
        Set<Long> categoryIds = criteria.categoryId() == null ? Set.of() : withDescendants(criteria.categoryId());
        OffsetDateTime now = OffsetDateTime.now();
        if (sortOption == SortOption.NEWEST) {
            return productRepository.findAll(
                    publicListings(criteria, categoryIds, now).and(featuredFirst(now)),
                    PageRequest.of(page, size)
            );
        }
        return productRepository.findAll(
                publicListings(criteria, categoryIds, now),
                PageRequest.of(page, size, sortOption.sort)
        );
    }

    /**
     * Public listings matching {@code criteria} that were approved in {@code (since, until]},
     * excluding the given user's own listings. Used for saved-search alerts.
     */
    public long countApprovedBetween(Criteria criteria, OffsetDateTime since, OffsetDateTime until, Long excludeOwnerUserId) {
        Set<Long> categoryIds = criteria.categoryId() == null ? Set.of() : withDescendants(criteria.categoryId());
        Specification<Product> approvedInWindow = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThan(root.get("reviewedAt"), since));
            predicates.add(cb.lessThanOrEqualTo(root.get("reviewedAt"), until));
            if (excludeOwnerUserId != null) {
                predicates.add(cb.notEqual(root.get("vendor").get("user").get("id"), excludeOwnerUserId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return productRepository.count(publicListings(criteria, categoryIds, until).and(approvedInWindow));
    }

    private Specification<Product> publicListings(Criteria criteria, Set<Long> categoryIds, OffsetDateTime now) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("active")));
            predicates.add(cb.isNull(root.get("removedAt")));
            predicates.add(cb.greaterThan(root.get("expiresAt"), now));
            predicates.add(cb.equal(root.get("moderationStatus"), ModerationStatus.APPROVED));
            predicates.add(cb.isFalse(root.get("vendor").get("user").get("vacationMode")));

            // Every word must appear in the title or the description.
            for (String term : searchTerms(criteria.query())) {
                String pattern = "%" + escapeLike(term) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\')
                ));
            }
            if (!categoryIds.isEmpty()) {
                predicates.add(root.get("category").get("id").in(categoryIds));
            }
            if (criteria.conditions() != null && !criteria.conditions().isEmpty()) {
                predicates.add(root.get("condition").in(criteria.conditions()));
            }
            if (criteria.delivery() == Delivery.HAND) {
                predicates.add(cb.isTrue(root.get("handDelivery")));
            } else if (criteria.delivery() == Delivery.SHIPPING) {
                predicates.add(cb.isTrue(root.get("shippingAvailable")));
            }
            if (criteria.area() != null) {
                predicates.add(withinArea(root, cb, criteria.area()));
            }
            if (criteria.sellerUserId() != null) {
                predicates.add(cb.equal(root.get("vendor").get("user").get("id"), criteria.sellerUserId()));
            }
            if (criteria.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), criteria.minPrice()));
            }
            if (criteria.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), criteria.maxPrice()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Orders boosted listings first, then newest first. Adds no condition. */
    private Specification<Product> featuredFirst(OffsetDateTime now) {
        return (root, query, cb) -> {
            // The count query must not get an ORDER BY.
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                Expression<Integer> featured = cb.<Integer>selectCase()
                        .when(cb.greaterThan(root.get("boostedUntil"), now), 1)
                        .otherwise(0);
                query.orderBy(cb.desc(featured), cb.desc(root.get("listedAt")), cb.desc(root.get("id")));
            }
            return cb.conjunction();
        };
    }

    /**
     * A cheap bounding box (so an index on the coordinates can be used) plus the exact
     * great-circle distance. Listings without coordinates never match.
     */
    private Predicate withinArea(Root<Product> root, CriteriaBuilder cb, Area area) {
        double latDelta = Math.toDegrees(area.radiusKm() / EARTH_RADIUS_KM);
        double lngDelta = Math.toDegrees(area.radiusKm() / EARTH_RADIUS_KM / Math.max(Math.cos(Math.toRadians(area.latitude())), 0.01));
        Expression<Double> lat = root.get("latitude");
        Expression<Double> lng = root.get("longitude");

        Expression<Double> latRad = cb.function("radians", Double.class, lat);
        Expression<Double> lngRad = cb.function("radians", Double.class, lng);
        double originLat = Math.toRadians(area.latitude());
        double originLng = Math.toRadians(area.longitude());
        // cos(c) = sin(a)sin(b) + cos(a)cos(b)cos(dLng); comparing cos(c) avoids acos rounding issues.
        Expression<Double> cosDistance = cb.sum(
                cb.prod(Math.sin(originLat), cb.function("sin", Double.class, latRad)),
                cb.prod(
                        cb.prod(Math.cos(originLat), cb.function("cos", Double.class, latRad)),
                        cb.function("cos", Double.class, cb.diff(lngRad, originLng))
                )
        );
        return cb.and(
                cb.isNotNull(lat),
                cb.isNotNull(lng),
                cb.between(lat, area.latitude() - latDelta, area.latitude() + latDelta),
                cb.between(lng, area.longitude() - lngDelta, area.longitude() + lngDelta),
                cb.greaterThanOrEqualTo(cosDistance, Math.cos(area.radiusKm() / EARTH_RADIUS_KM))
        );
    }

    private List<String> searchTerms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return Arrays.stream(query.toLowerCase(Locale.ROOT).trim().split("\\s+"))
                .filter(term -> !term.isBlank())
                .distinct()
                .limit(MAX_SEARCH_TERMS)
                .toList();
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** A category filter also matches listings in all of its sub-categories. */
    private Set<Long> withDescendants(Long categoryId) {
        Map<Long, List<Long>> childrenByParent = categoryRepository.findAll().stream()
                .filter(category -> category.getParent() != null)
                .collect(Collectors.groupingBy(
                        category -> category.getParent().getId(),
                        Collectors.mapping(Category::getId, Collectors.toList())
                ));
        Set<Long> result = new HashSet<>();
        Deque<Long> pending = new ArrayDeque<>(List.of(categoryId));
        while (!pending.isEmpty()) {
            Long current = pending.pop();
            if (result.add(current)) {
                pending.addAll(childrenByParent.getOrDefault(current, List.of()));
            }
        }
        return result;
    }
}
