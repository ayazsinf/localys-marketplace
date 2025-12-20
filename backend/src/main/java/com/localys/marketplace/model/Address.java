package com.localys.marketplace.model;


import com.localys.marketplace.model.enums.AddressType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "addresses")
public class Address extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AddressType type;

    @Column(length = 80)
    private String label;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Column(length = 40)
    private String phone;

    @Column(length = 200, nullable = false)
    private String line1;

    @Column(length = 200)
    private String line2;

    @Column(length = 120, nullable = false)
    private String city;

    @Column(name = "postal_code", length = 20, nullable = false)
    private String postalCode;

    @Column(length = 2, nullable = false)
    private String country = "FR";

    @Column(name = "is_default_shipping", nullable = false)
    private boolean defaultShipping;

    @Column(name = "is_default_billing", nullable = false)
    private boolean defaultBilling;
}
