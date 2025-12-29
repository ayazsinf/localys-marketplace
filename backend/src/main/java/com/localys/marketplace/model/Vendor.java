package com.localys.marketplace.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.localys.marketplace.model.enums.VendorStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "vendors")
public class Vendor extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VendorStatus status = VendorStatus.PENDING;

    @Column(name = "shop_name", nullable = false, length = 160)
    private String shopName;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(name = "vat_number", length = 80)
    private String vatNumber;

    @Column(length = 64)
    private String iban;
}
