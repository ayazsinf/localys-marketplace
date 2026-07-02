package com.localys.marketplace.service;

import com.localys.marketplace.config.KeycloakProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
public class KeycloakTokenService {

    private final KeycloakProperties properties;
    private final RestClient restClient;

    public KeycloakTokenService(KeycloakProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    public TokenResponse login(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", properties.getClientId());
        form.add("username", username);
        form.add("password", password);
        return requestToken(form);
    }

    public TokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", properties.getClientId());
        form.add("refresh_token", refreshToken);
        return requestToken(form);
    }

    private TokenResponse requestToken(MultiValueMap<String, String> form) {
        return restClient.post()
                .uri(properties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
    }

    public record TokenResponse(
            String access_token,
            String refresh_token,
            Long expires_in,
            Long refresh_expires_in
    ) {
    }
}
