package com.localys.marketplace.config;

import com.localys.marketplace.service.CustomUserDetailsService;
import com.localys.marketplace.service.KeycloakPrincipalService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

public class JwtHandshakeInterceptor implements HandshakeInterceptor {
    private static final String ACCESS_COOKIE = "localys_access";

    private final KeycloakPrincipalService keycloakPrincipalService;

    public JwtHandshakeInterceptor(KeycloakPrincipalService keycloakPrincipalService) {
        this.keycloakPrincipalService = keycloakPrincipalService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        String token = resolveToken(request);
        if (token == null) {
            return false;
        }

        UserDetails userDetails = resolveUserDetails(token);
        if (userDetails == null) {
            return false;
        }
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
        attributes.put("auth", authentication);
        return true;
    }

    private UserDetails resolveUserDetails(String token) {
        try {
            return keycloakPrincipalService.authenticate(token);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // no-op
    }

    private String resolveToken(ServerHttpRequest request) {
        List<String> authHeaders = request.getHeaders().get("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String header = authHeaders.get(0);
            if (header.startsWith("Bearer ")) {
                return header.substring(7);
            }
        }

        MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams();
        String accessToken = params.getFirst("access_token");
        if (accessToken != null && !accessToken.isBlank()) {
            return accessToken;
        }
        List<String> cookieHeaders = request.getHeaders().get("Cookie");
        if (cookieHeaders != null) {
            for (String cookieHeader : cookieHeaders) {
                String sessionToken = resolveCookieValue(cookieHeader);
                if (sessionToken != null) {
                    return sessionToken;
                }
            }
        }
        return null;
    }

    private String resolveCookieValue(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return null;
        }
        String[] cookies = cookieHeader.split(";");
        for (String cookie : cookies) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && ACCESS_COOKIE.equals(parts[0]) && !parts[1].isBlank()) {
                return parts[1];
            }
        }
        return null;
    }
}
