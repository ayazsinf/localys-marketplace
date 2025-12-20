package com.localys.marketplace.model;

import com.localys.marketplace.model.enums.USER_ROLE;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "users")
public class UserEntity extends AuditableEntity {


    @Column(name = "keycloak_id", nullable = false, unique = true, length = 64)
    private String keycloakId;

    @Column(length = 150)
    private String username;

    @Column(length = 254)
    private String email;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(length = 40)
    private String phone;


    @Column(nullable = false, length = 200)
    private String password;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private USER_ROLE role;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Address> addresses = new ArrayList<>();
}
