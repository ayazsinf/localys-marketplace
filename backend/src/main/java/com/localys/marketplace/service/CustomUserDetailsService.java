package com.localys.marketplace.service;


import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.enums.USER_ROLE;
import com.localys.marketplace.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class CustomUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;
  private final EmailService emailService;

    public CustomUserDetailsService(UserRepository repo, EmailService emailService) {
        this.userRepository = repo;
        this.emailService = emailService;
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found: " + username)
                );

        return new CustomUserDetails(user);
    }

    public CustomUserDetails loadOrCreateFromJwt(Jwt jwt) {
        String keycloakId = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        String displayName = jwt.getClaimAsString("name");

        Optional<UserEntity> byKeycloak = userRepository.findByKeycloakId(keycloakId);
        if (byKeycloak.isPresent()) {
            UserEntity existing = byKeycloak.get();
            syncUserBasics(existing, username, email, displayName, keycloakId);
            return new CustomUserDetails(userRepository.save(existing));
        }

        if (username != null && !username.isBlank()) {
            Optional<UserEntity> byUsername = userRepository.findByUsername(username);
            if (byUsername.isPresent()) {
                UserEntity existing = byUsername.get();
                syncUserBasics(existing, username, email, displayName, keycloakId);
                return new CustomUserDetails(userRepository.save(existing));
            }
        }

        UserEntity created = new UserEntity();
        created.setKeycloakId(keycloakId);
        created.setUsername(username != null ? username : keycloakId);
        created.setEmail(email);
        created.setDisplayName(displayName);
        created.setPassword(UUID.randomUUID().toString());
        created.setEnabled(true);
        created.setRole(USER_ROLE.ROLE_USER);

        UserEntity saved = userRepository.save(created);
        emailService.sendWelcomeEmail(saved);
        return new CustomUserDetails(saved);
    }

    private void syncUserBasics(
            UserEntity user,
            String username,
            String email,
            String displayName,
            String keycloakId
    ) {
        if ((user.getKeycloakId() == null || user.getKeycloakId().isBlank()) && keycloakId != null) {
            user.setKeycloakId(keycloakId);
        }
        if (username != null && !username.isBlank()) {
            user.setUsername(username);
        }
        if (email != null && !email.isBlank()) {
            user.setEmail(email);
        }
        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName);
        }
    }
}
