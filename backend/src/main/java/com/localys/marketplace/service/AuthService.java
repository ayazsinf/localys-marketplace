package com.localys.marketplace.service;

import com.localys.marketplace.dto.RegisterUserRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class AuthService {

    private final KeycloakTokenService keycloakTokenService;
    private final KeycloakAdminService keycloakAdminService;

    public AuthService(KeycloakTokenService keycloakTokenService,
                       KeycloakAdminService keycloakAdminService) {
        this.keycloakTokenService = keycloakTokenService;
        this.keycloakAdminService = keycloakAdminService;
    }

    public AuthTokens login(String username, String rawPassword) {
        try {
            KeycloakTokenService.TokenResponse tokenResponse = keycloakTokenService.login(username, rawPassword);
            if (tokenResponse != null && tokenResponse.access_token() != null && tokenResponse.refresh_token() != null) {
                return new AuthTokens(tokenResponse.access_token(), tokenResponse.refresh_token());
            }
        } catch (RestClientException ignored) {
            throw new BadCredentialsException("Invalid username or password");
        }
        throw new BadCredentialsException("Invalid username or password");
    }

    public AuthTokens register(RegisterUserRequest request) {
        keycloakAdminService.createUser(request);
        return login(request.username(), request.password());
    }

    public AuthTokens refreshTokens(String refreshToken) {
        try {
            KeycloakTokenService.TokenResponse tokenResponse = keycloakTokenService.refresh(refreshToken);
            if (tokenResponse != null && tokenResponse.access_token() != null && tokenResponse.refresh_token() != null) {
                return new AuthTokens(tokenResponse.access_token(), tokenResponse.refresh_token());
            }
        } catch (RestClientException ignored) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        throw new BadCredentialsException("Invalid refresh token");
    }

    public record AuthTokens(String accessToken, String refreshToken) {}
}
