package com.localys.marketplace.controller;

import com.localys.marketplace.dto.RegisterUserRequest;
import com.localys.marketplace.model.LoginRequest;
import com.localys.marketplace.repository.UserRepository;
import com.localys.marketplace.service.AuthService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private static final String ACCESS_COOKIE = "localys_access";
    private static final String REFRESH_COOKIE = "localys_refresh";
    private static final String LEGACY_SESSION_COOKIE = "localys_session";
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(7);

    private final AuthService authService;
    private final UserRepository userRepository;
    public AuthController(AuthService service, UserRepository userRepository) {
        this.authService = service;
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest req,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        AuthService.AuthTokens tokens = authService.login(req.username(), req.password());
        addAuthCookies(response, tokens, request);
        return ResponseEntity.ok(new AuthResponse(req.username()));
    }

    @GetMapping("/test-db")
    public Object test() {

        return userRepository.findByUsername("fatih");
    }


    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterUserRequest registerRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        AuthService.AuthTokens tokens = authService.register(registerRequest);
        addAuthCookies(response, tokens, request);
        return ResponseEntity.ok(new AuthResponse(registerRequest.username()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = readCookie(request, REFRESH_COOKIE);
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String accessToken = authService.refreshAccessToken(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(ACCESS_COOKIE, accessToken, ACCESS_TTL, request).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(LEGACY_SESSION_COOKIE, "", Duration.ZERO, request).toString());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(ACCESS_COOKIE, "", Duration.ZERO, request).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(REFRESH_COOKIE, "", Duration.ZERO, request).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(LEGACY_SESSION_COOKIE, "", Duration.ZERO, request).toString());
        return ResponseEntity.noContent().build();
    }

    // Basit response DTO
    public record AuthResponse(String username) {}

    private void addAuthCookies(
            HttpServletResponse response,
            AuthService.AuthTokens tokens,
            HttpServletRequest request
    ) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                buildCookie(ACCESS_COOKIE, tokens.accessToken(), ACCESS_TTL, request).toString()
        );
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                buildCookie(REFRESH_COOKIE, tokens.refreshToken(), REFRESH_TTL, request).toString()
        );
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                buildCookie(LEGACY_SESSION_COOKIE, "", Duration.ZERO, request).toString()
        );
    }

    private ResponseCookie buildCookie(String name, String value, Duration maxAge, HttpServletRequest request) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(isSecureRequest(request))
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return request.isSecure() || "https".equalsIgnoreCase(forwardedProto);
    }

}
