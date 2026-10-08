package com.localys.marketplace.service;

import com.localys.marketplace.model.Category;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.enums.ModerationStatus;
import com.localys.marketplace.repository.CategoryRepository;
import com.localys.marketplace.repository.ProductRepository;
import jakarta.persistence.criteria.Predicate;
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

    public enum SortOption {
        NEWEST(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
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

    public record Criteria(
            String query,
            Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String sort,
            Integer page,
            Integer size
    ) {
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
        Sort sort = SortOption.from(criteria.sort()).sort;
        Set<Long> categoryIds = criteria.categoryId() == null ? Set.of() : withDescendants(criteria.categoryId());
        return productRepository.findAll(
                publicListings(criteria, categoryIds, OffsetDateTime.now()),
                PageRequest.of(page, size, sort)
        );
    }

    private Specification<Product> publicListings(Criteria criteria, Set<Long> categoryIds, OffsetDateTime now) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("active")));
            predicates.add(cb.isNull(root.get("removedAt")));
            predicates.add(cb.greaterThan(root.get("expiresAt"), now));
            predicates.add(cb.equal(root.get("moderationStatus"), ModerationStatus.APPROVED));

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
            if (criteria.minPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), criteria.minPrice()));
            }
            if (criteria.maxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), criteria.maxPrice()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
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
