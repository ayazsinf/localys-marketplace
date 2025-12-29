package com.localys.marketplace.service;


import com.localys.marketplace.dto.RegisterUserRequest;
import com.localys.marketplace.exceptions.UserAlreadyExistsException;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.enums.USER_ROLE;
import com.localys.marketplace.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository,
                       CustomUserDetailsService uds,
                       PasswordEncoder encoder) {
        this.userDetailsService = uds;
        this.passwordEncoder = encoder;
        this.userRepository = userRepository;
    }

    public String login(String username, String rawPassword) {
        UserDetails user = userDetailsService.loadUserByUsername(username);

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        // JWT devre disi (Keycloak'a gecis icin)
        return "";
    }

    @Transactional
    public String register(RegisterUserRequest request) {
        // 1) Username/email mevcut mu?
        if (userRepository.existsByUsername(request.username())) {
            throw new UserAlreadyExistsException("Username already exists");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException("Email already exists");
        }

        // 2) User oluştur
        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        user.setRole(USER_ROLE.ROLE_USER);

        userRepository.save(user);

        // 3) İstersek direkt token üret
        // JWT devre disi (Keycloak'a gecis icin)
        return "";
    }
}
