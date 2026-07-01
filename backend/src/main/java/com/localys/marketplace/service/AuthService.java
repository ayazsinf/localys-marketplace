package com.localys.marketplace.service;

import com.localys.marketplace.dto.RegisterUserRequest;
import com.localys.marketplace.exceptions.UserAlreadyExistsException;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.enums.USER_ROLE;
import com.localys.marketplace.repository.UserRepository;
import com.localys.marketplace.util.JwtUtil;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {

    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
                       CustomUserDetailsService userDetailsService,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService,
                       JwtUtil jwtUtil) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.jwtUtil = jwtUtil;
    }

    public AuthTokens login(String username, String rawPassword) {
        UserDetails user = userDetailsService.loadUserByUsername(username);

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        return issueTokens(user.getUsername());
    }

    @Transactional
    public AuthTokens register(RegisterUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UserAlreadyExistsException("Username already exists");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException("Email already exists");
        }

        UserEntity user = new UserEntity();
        user.setKeycloakId("local_" + UUID.randomUUID());
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setDisplayName(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        user.setRole(USER_ROLE.ROLE_USER);

        UserEntity saved = userRepository.save(user);
        emailService.sendWelcomeEmail(saved);

        return issueTokens(saved.getUsername());
    }

    public String refreshAccessToken(String refreshToken) {
        if (!jwtUtil.isRefreshTokenValid(refreshToken)) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        String username = jwtUtil.extractUsername(refreshToken);
        userDetailsService.loadUserByUsername(username);
        return jwtUtil.generateAccessToken(username);
    }

    private AuthTokens issueTokens(String username) {
        return new AuthTokens(
                jwtUtil.generateAccessToken(username),
                jwtUtil.generateRefreshToken(username)
        );
    }

    public record AuthTokens(String accessToken, String refreshToken) {}
}
