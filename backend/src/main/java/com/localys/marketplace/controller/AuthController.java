package com.localys.marketplace.controller;

import com.localys.marketplace.dto.RegisterUserRequest;
import com.localys.marketplace.model.LoginRequest;
import com.localys.marketplace.repository.UserRepository;
import com.localys.marketplace.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    public AuthController(AuthService service, UserRepository userRepository) {
        this.authService = service;
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        // JWT devre disi (Keycloak'a gecis icin)
        authService.login(req.username(), req.password());
        return ResponseEntity.ok(Map.of("token", ""));
    }

    @GetMapping("/test-db")
    public Object test() {

        return userRepository.findByUsername("fatih");
    }


    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        // JWT devre disi (Keycloak'a gecis icin)
        authService.register(request);
        return ResponseEntity.ok(new AuthResponse(""));
    }

    // Basit response DTO
    public record AuthResponse(String token) {}

}
