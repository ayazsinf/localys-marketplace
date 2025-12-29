package com.localys.marketplace.controller;

import com.localys.marketplace.model.CustomUserDetails;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.Vendor;
import com.localys.marketplace.repository.VendorRepository;
import com.localys.marketplace.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.FORBIDDEN;

@RestController
@RequestMapping("/api/vendor")
@PreAuthorize("hasRole('VENDOR')") // Only vendor role can access
public class VendorController {

    @Autowired
    private ProductService productService;

    @Autowired
    private VendorRepository vendorRepository;

    // Vendor products list
    @GetMapping("/products")
    public ResponseEntity<List<Product>> getVendorProducts(
            @AuthenticationPrincipal CustomUserDetails principal) {
        Vendor vendor = getVendorOrThrow(principal);
        List<Product> products = productService.getProductsByVendor(vendor.getId());
        return ResponseEntity.ok(products);
    }

    // Add new product
    @PostMapping("/products")
    public ResponseEntity<Product> addProduct(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestBody Product product) {
        Vendor vendor = getVendorOrThrow(principal);
        Product createdProduct = productService.addProductForVendor(product, vendor);
        return ResponseEntity.ok(createdProduct);
    }

    // Update product
    @PutMapping("/products/{id}")
    public ResponseEntity<Product> updateProduct(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id,
            @RequestBody Product product) {
        Vendor vendor = getVendorOrThrow(principal);
        Product updatedProduct = productService.updateProductForVendor(id, product, vendor);
        return ResponseEntity.ok(updatedProduct);
    }

    // Delete product
    @DeleteMapping("/products/{id}")
    public ResponseEntity<?> deleteProduct(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long id) {
        Vendor vendor = getVendorOrThrow(principal);
        productService.deleteProductForVendor(id, vendor);
        return ResponseEntity.ok().build();
    }

    // Vendor profile
    @GetMapping("/profile")
    public ResponseEntity<Vendor> getProfile(
            @AuthenticationPrincipal CustomUserDetails principal) {
        Vendor vendor = getVendorOrThrow(principal);
        return ResponseEntity.ok(vendor);
    }

    @PutMapping("/profile")
    public ResponseEntity<Vendor> updateProfile(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestBody VendorProfileRequest request) {
        Vendor vendor = getVendorOrThrow(principal);
        if (request.getShopName() != null) {
            vendor.setShopName(request.getShopName());
        }
        if (request.getLegalName() != null) {
            vendor.setLegalName(request.getLegalName());
        }
        if (request.getVatNumber() != null) {
            vendor.setVatNumber(request.getVatNumber());
        }
        if (request.getIban() != null) {
            vendor.setIban(request.getIban());
        }
        Vendor saved = vendorRepository.save(vendor);
        return ResponseEntity.ok(saved);
    }

    private Vendor getVendorOrThrow(CustomUserDetails principal) {
        if (principal == null) {
            throw new ResponseStatusException(FORBIDDEN, "Vendor access required");
        }
        return vendorRepository.findByUserId(principal.getUserId())
                .orElseThrow(() -> new ResponseStatusException(FORBIDDEN, "Vendor access required"));
    }

    public static class VendorProfileRequest {
        private String shopName;
        private String legalName;
        private String vatNumber;
        private String iban;

        public String getShopName() {
            return shopName;
        }

        public void setShopName(String shopName) {
            this.shopName = shopName;
        }

        public String getLegalName() {
            return legalName;
        }

        public void setLegalName(String legalName) {
            this.legalName = legalName;
        }

        public String getVatNumber() {
            return vatNumber;
        }

        public void setVatNumber(String vatNumber) {
            this.vatNumber = vatNumber;
        }

        public String getIban() {
            return iban;
        }

        public void setIban(String iban) {
            this.iban = iban;
        }
    }
}
