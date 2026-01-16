package com.localys.marketplace.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtDecoder jwtDecoder;
    private final KeycloakJwtAuthConverter authConverter;

    public JwtHandshakeInterceptor(JwtDecoder jwtDecoder, KeycloakJwtAuthConverter authConverter) {
        this.jwtDecoder = jwtDecoder;
        this.authConverter = authConverter;
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
        Jwt jwt = jwtDecoder.decode(token);
        AbstractAuthenticationToken authentication = authConverter.convert(jwt);
        attributes.put("auth", authentication);
        return true;
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
        return null;
    }
}
