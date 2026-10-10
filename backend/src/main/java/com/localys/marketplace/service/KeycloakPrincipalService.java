package com.localys.marketplace.service;

import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.enums.USER_ROLE;
import com.localys.marketplace.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class KeycloakPrincipalService {

    private final JwtDecoder jwtDecoder;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public KeycloakPrincipalService(JwtDecoder jwtDecoder, UserRepository userRepository, EmailService emailService) {
        this.jwtDecoder = jwtDecoder;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    @Transactional
    public CustomUserDetails authenticate(String token) {
        Jwt jwt = jwtDecoder.decode(token);
        List<SimpleGrantedAuthority> authorities = extractRoles(jwt).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        UserEntity user = syncUser(jwt, authorities);
        return new CustomUserDetails(user, authorities);
    }

    private UserEntity syncUser(Jwt jwt, Collection<SimpleGrantedAuthority> authorities) {
        String subject = jwt.getSubject();
        String username = firstNonBlank(
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"),
                subject
        );

        UserEntity user = userRepository.findByKeycloakId(subject)
                .or(() -> userRepository.findByUsername(username))
                .orElseGet(UserEntity::new);

        if (user.getKeycloakId() == null || user.getKeycloakId().isBlank() || user.getKeycloakId().startsWith("local_")) {
            user.setKeycloakId(subject);
        }
        boolean isNew = user.getId() == null;
        user.setUsername(username);
        // Profile fields come from the token only until the user edits them (PUT /api/users/me);
        // overwriting them on every request silently reverted those edits.
        if (isBlank(user.getEmail())) {
            user.setEmail(jwt.getClaimAsString("email"));
        }
        if (isBlank(user.getDisplayName())) {
            user.setDisplayName(firstNonBlank(jwt.getClaimAsString("name"), jwt.getClaimAsString("given_name")));
        }
        if (isBlank(user.getPhone())) {
            user.setPhone(jwt.getClaimAsString("phone_number"));
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            user.setPassword("{keycloak}");
        }
        user.setEnabled(true);
        user.setRole(resolveDisplayRole(authorities));
        UserEntity saved = userRepository.save(user);
        if (isNew) {
            emailService.sendWelcomeEmail(saved);
        }
        return saved;
    }

    private List<String> extractRoles(Jwt jwt) {
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (!(realmAccess instanceof Map<?, ?> realmAccessMap)) {
            return List.of("ROLE_USER");
        }
        Object roles = realmAccessMap.get("roles");
        if (!(roles instanceof Collection<?> roleValues)) {
            return List.of("ROLE_USER");
        }
        List<String> mappedRoles = roleValues.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(this::normalizeRole)
                .distinct()
                .toList();
        return mappedRoles.isEmpty() ? List.of("ROLE_USER") : mappedRoles;
    }

    private String normalizeRole(String role) {
        return role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase();
    }

    private USER_ROLE resolveDisplayRole(Collection<SimpleGrantedAuthority> authorities) {
        Optional<String> admin = authorities.stream()
                .map(SimpleGrantedAuthority::getAuthority)
                .filter("ROLE_ADMIN"::equals)
                .findFirst();
        return admin.isPresent() ? USER_ROLE.ROLE_ADMIN : USER_ROLE.ROLE_USER;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
