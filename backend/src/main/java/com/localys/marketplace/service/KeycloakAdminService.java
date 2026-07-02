package com.localys.marketplace.service;

import com.localys.marketplace.config.KeycloakProperties;
import com.localys.marketplace.dto.RegisterUserRequest;
import com.localys.marketplace.exceptions.UserAlreadyExistsException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Service
public class KeycloakAdminService {

    private final KeycloakProperties properties;
    private final RestClient restClient;

    public KeycloakAdminService(KeycloakProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.serverUrl())
                .build();
    }

    public void createUser(RegisterUserRequest request) {
        String accessToken = adminAccessToken();
        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri("/admin/realms/{realm}/users", properties.realmName())
                    .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(userPayload(request))
                    .retrieve()
                    .toBodilessEntity();

            String userId = userIdFromLocation(response.getHeaders().getLocation());
            if (userId == null || userId.isBlank()) {
                userId = findUserIdByUsername(accessToken, request.username());
            }
            assignRealmRole(accessToken, userId, "ROLE_USER");
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                throw new UserAlreadyExistsException("Username or email already exists");
            }
            throw ex;
        }
    }

    private Map<String, Object> userPayload(RegisterUserRequest request) {
        return Map.of(
                "username", request.username(),
                "email", request.email(),
                "firstName", request.username(),
                "lastName", "Localys",
                "enabled", true,
                "emailVerified", false,
                "requiredActions", List.of(),
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", request.password(),
                        "temporary", false
                ))
        );
    }

    private String adminAccessToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.getAdminClientId());
        form.add("client_secret", properties.getAdminClientSecret());

        KeycloakTokenService.TokenResponse response = RestClient.create()
                .post()
                .uri(properties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KeycloakTokenService.TokenResponse.class);

        if (response == null || response.access_token() == null || response.access_token().isBlank()) {
            throw new RestClientException("Keycloak admin token response is empty");
        }
        return response.access_token();
    }

    private String findUserIdByUsername(String accessToken, String username) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> users = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/admin/realms/{realm}/users")
                        .queryParam("username", username)
                        .queryParam("exact", true)
                        .build(properties.realmName()))
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(List.class);

        if (users == null || users.isEmpty() || users.get(0).get("id") == null) {
            throw new RestClientException("Created Keycloak user could not be found");
        }
        return users.get(0).get("id").toString();
    }

    private void assignRealmRole(String accessToken, String userId, String roleName) {
        @SuppressWarnings("unchecked")
        Map<String, Object> role = restClient.get()
                .uri("/admin/realms/{realm}/roles/{role}", properties.realmName(), roleName)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .retrieve()
                .body(Map.class);
        if (role == null || role.get("id") == null) {
            throw new RestClientException("Keycloak role not found: " + roleName);
        }

        restClient.post()
                .uri("/admin/realms/{realm}/users/{userId}/role-mappings/realm", properties.realmName(), userId)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity();
    }

    private String userIdFromLocation(URI location) {
        if (location == null) {
            return null;
        }
        String path = location.getPath();
        int index = path.lastIndexOf('/');
        return index >= 0 ? path.substring(index + 1) : null;
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
