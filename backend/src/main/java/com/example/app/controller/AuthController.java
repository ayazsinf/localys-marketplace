package com.example.app.controller;

import com.example.app.dto.RegisterUserRequest;
import com.example.app.model.LoginRequest;
import com.example.app.repository.UserRepository;
import com.example.app.service.AuthService;
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
        String token = authService.login(req.username(), req.password());
        return ResponseEntity.ok(Map.of("token", token));
    }

    @GetMapping("/test-db")
    public Object test() {

        return userRepository.findByUsername("admin");
    }


    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        String token = authService.register(request);
        return ResponseEntity.ok(new AuthResponse(token));
    }

    // Basit response DTO
    public record AuthResponse(String token) {}

}
