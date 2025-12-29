package com.localys.marketplace.service;

import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.Vendor;
import com.localys.marketplace.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    public List<Product> getProductsByVendor(Long vendorId) {
        return productRepository.findByVendorId(vendorId);
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    public Product addProductForVendor(Product product, Vendor vendor) {
        product.setVendor(vendor);
        return productRepository.save(product);
    }

    public Product updateProductForVendor(Long id, Product productDetails, Vendor vendor) {
        Product product = productRepository.findByIdAndVendorId(id, vendor.getId())
                .orElseThrow(() -> new RuntimeException("Product not found"));
        product.setName(productDetails.getName());
        product.setPrice(productDetails.getPrice());
        product.setDescription(productDetails.getDescription());
        product.setBrand(productDetails.getBrand());
        product.setStockQty(productDetails.getStockQty());
        product.setActive(productDetails.isActive());
        product.setCurrency(productDetails.getCurrency());
        product.setSku(productDetails.getSku());
        product.setCategory(productDetails.getCategory());
        return productRepository.save(product);
    }

    public void deleteProductForVendor(Long id, Vendor vendor) {
        Product product = productRepository.findByIdAndVendorId(id, vendor.getId())
                .orElseThrow(() -> new RuntimeException("Product not found"));
        productRepository.delete(product);
    }
}
