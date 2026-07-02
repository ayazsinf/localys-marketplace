package com.localys.marketplace.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final RestClient restClient;

    public LocationController(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl("https://api-adresse.data.gouv.fr")
                .build();
    }

    @GetMapping("/search")
    public ResponseEntity<LocationSearchResponse> search(@RequestParam("q") String query) {
        String trimmedQuery = query == null ? "" : query.trim();
        if (trimmedQuery.length() < 3) {
            return ResponseEntity.ok(new LocationSearchResponse(List.of()));
        }

        AddressApiResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search/")
                        .queryParam("q", trimmedQuery)
                        .queryParam("limit", 5)
                        .queryParamIfPresent("type", isPostalCode(trimmedQuery)
                                ? java.util.Optional.of("municipality")
                                : java.util.Optional.empty())
                        .build())
                .retrieve()
                .body(AddressApiResponse.class);

        List<LocationResult> results = response == null || response.features() == null
                ? List.of()
                : response.features().stream()
                .map(this::toLocationResult)
                .filter(result -> result != null)
                .toList();

        return ResponseEntity.ok(new LocationSearchResponse(results));
    }

    private boolean isPostalCode(String query) {
        return query.matches("\\d{4,5}");
    }

    private LocationResult toLocationResult(AddressFeature feature) {
        if (feature == null || feature.properties() == null || feature.geometry() == null) {
            return null;
        }
        List<Double> coordinates = feature.geometry().coordinates();
        if (coordinates == null || coordinates.size() < 2) {
            return null;
        }
        AddressProperties properties = feature.properties();
        String city = properties.city() != null ? properties.city() : properties.municipality();
        String label = properties.label();
        if ((label == null || label.isBlank()) && (properties.postcode() != null || city != null)) {
            label = String.join(" ", List.of(
                    properties.postcode() == null ? "" : properties.postcode(),
                    city == null ? "" : city
            )).trim();
        }
        if (label == null || label.isBlank()) {
            return null;
        }
        return new LocationResult(
                label,
                coordinates.get(1),
                coordinates.get(0),
                properties.postcode(),
                city
        );
    }

    public record LocationSearchResponse(List<LocationResult> results) {
    }

    public record LocationResult(
            String label,
            Double latitude,
            Double longitude,
            String postalCode,
            String city
    ) {
    }

    private record AddressApiResponse(List<AddressFeature> features) {
    }

    private record AddressFeature(AddressProperties properties, AddressGeometry geometry) {
    }

    private record AddressProperties(
            String label,
            String postcode,
            String city,
            String municipality
    ) {
    }

    private record AddressGeometry(List<Double> coordinates) {
    }
}
