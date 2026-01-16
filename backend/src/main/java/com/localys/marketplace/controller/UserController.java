package com.localys.marketplace.controller;

import com.localys.marketplace.model.Address;
import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.enums.AddressType;
import com.localys.marketplace.repository.AddressRepository;
import com.localys.marketplace.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;

    public UserController(UserRepository userRepository, AddressRepository addressRepository) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        UserEntity user = principal.getUser();
        return ResponseEntity.ok(UserProfileResponse.from(user));
    }

    @PutMapping("/me")
    @Transactional
    public ResponseEntity<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestBody UserProfileUpdateRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        UserEntity user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));

        if (request.displayName() != null) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.email() != null) {
            user.setEmail(request.email().trim());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone().trim());
        }

        UserEntity saved = userRepository.save(user);
        return ResponseEntity.ok(UserProfileResponse.from(saved));
    }

    @PostMapping("/me/addresses")
    @Transactional
    public ResponseEntity<AddressResponse> addAddress(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestBody AddressRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        UserEntity user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));

        Address address = new Address();
        address.setUser(user);
        applyAddressRequest(address, request);
        normalizeDefaults(user, address);

        Address saved = addressRepository.save(address);
        return ResponseEntity.ok(AddressResponse.from(saved));
    }

    @PutMapping("/me/addresses/{id}")
    @Transactional
    public ResponseEntity<AddressResponse> updateAddress(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @RequestBody AddressRequest request
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        Address address = addressRepository.findByIdAndUser_Id(id, principal.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Address not found"));

        applyAddressRequest(address, request);
        normalizeDefaults(address.getUser(), address);

        Address saved = addressRepository.save(address);
        return ResponseEntity.ok(AddressResponse.from(saved));
    }

    @DeleteMapping("/me/addresses/{id}")
    @Transactional
    public ResponseEntity<Void> deleteAddress(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        Address address = addressRepository.findByIdAndUser_Id(id, principal.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Address not found"));

        addressRepository.delete(address);
        return ResponseEntity.noContent().build();
    }

    private void applyAddressRequest(Address address, AddressRequest request) {
        if (request.line1() == null || request.line1().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "line1 is required");
        }
        if (request.city() == null || request.city().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "city is required");
        }
        if (request.postalCode() == null || request.postalCode().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "postalCode is required");
        }

        address.setType(parseAddressType(request.type()));
        address.setLabel(request.label());
        address.setFullName(request.fullName());
        address.setPhone(request.phone());
        address.setLine1(request.line1());
        address.setLine2(request.line2());
        address.setCity(request.city());
        address.setPostalCode(request.postalCode());
        if (request.country() != null && !request.country().isBlank()) {
            address.setCountry(request.country().trim().toUpperCase());
        }
        address.setDefaultShipping(request.defaultShipping());
        address.setDefaultBilling(request.defaultBilling());
    }

    private AddressType parseAddressType(String raw) {
        if (raw == null || raw.isBlank()) {
            return AddressType.SHIPPING;
        }
        try {
            return AddressType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid address type");
        }
    }

    private void normalizeDefaults(UserEntity user, Address updated) {
        if (user.getAddresses() == null) {
            return;
        }
        Long updatedId = updated.getId();
        if (updated.isDefaultShipping()) {
            user.getAddresses().stream()
                    .filter(address -> updatedId == null || !address.getId().equals(updatedId))
                    .forEach(address -> address.setDefaultShipping(false));
        }
        if (updated.isDefaultBilling()) {
            user.getAddresses().stream()
                    .filter(address -> updatedId == null || !address.getId().equals(updatedId))
                    .forEach(address -> address.setDefaultBilling(false));
        }
    }

    public record UserProfileUpdateRequest(
            String displayName,
            String email,
            String phone
    ) {}

    public record AddressRequest(
            String type,
            String label,
            String fullName,
            String phone,
            String line1,
            String line2,
            String city,
            String postalCode,
            String country,
            boolean defaultShipping,
            boolean defaultBilling
    ) {}

    public record AddressResponse(
            Long id,
            String type,
            String label,
            String fullName,
            String phone,
            String line1,
            String line2,
            String city,
            String postalCode,
            String country,
            boolean defaultShipping,
            boolean defaultBilling
    ) {
        public static AddressResponse from(Address address) {
            return new AddressResponse(
                    address.getId(),
                    address.getType() != null ? address.getType().name() : null,
                    address.getLabel(),
                    address.getFullName(),
                    address.getPhone(),
                    address.getLine1(),
                    address.getLine2(),
                    address.getCity(),
                    address.getPostalCode(),
                    address.getCountry(),
                    address.isDefaultShipping(),
                    address.isDefaultBilling()
            );
        }
    }

    public record UserProfileResponse(
            Long id,
            String username,
            String email,
            String displayName,
            String phone,
            String role,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<AddressResponse> addresses
    ) {
        public static UserProfileResponse from(UserEntity user) {
            List<AddressResponse> addresses = user.getAddresses() == null
                    ? List.of()
                    : user.getAddresses().stream().map(AddressResponse::from).toList();

            return new UserProfileResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getDisplayName(),
                    user.getPhone(),
                    user.getRole() != null ? user.getRole().name() : null,
                    user.getCreatedAt(),
                    user.getUpdatedAt(),
                    addresses
            );
        }
    }
}
