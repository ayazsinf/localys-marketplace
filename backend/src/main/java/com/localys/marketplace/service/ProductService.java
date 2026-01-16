package com.localys.marketplace.service;

import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.ProductImage;
import com.localys.marketplace.model.Vendor;
import com.localys.marketplace.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private MediaStorageService mediaStorageService;

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
        if (product.getSku() == null || product.getSku().isBlank()) {
            product.setSku(generateSku());
        }
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
        if (productDetails.getSku() != null && !productDetails.getSku().isBlank()) {
            product.setSku(productDetails.getSku());
        }
        product.setCategory(productDetails.getCategory());
        product.setLocationText(productDetails.getLocationText());
        product.setLatitude(productDetails.getLatitude());
        product.setLongitude(productDetails.getLongitude());
        return productRepository.save(product);
    }

    public void deleteProductForVendor(Long id, Vendor vendor) {
        Product product = productRepository.findByIdAndVendorId(id, vendor.getId())
                .orElseThrow(() -> new RuntimeException("Product not found"));
        productRepository.delete(product);
    }

    public List<String> addImagesForVendor(Long id, Vendor vendor, List<MultipartFile> files) {
        Product product = productRepository.findByIdAndVendorId(id, vendor.getId())
                .orElseThrow(() -> new RuntimeException("Product not found"));
        List<String> urls = mediaStorageService.storeListingImages(product.getId(), files);
        if (urls.isEmpty()) {
            return urls;
        }
        int sortOrderStart = product.getImages().size();
        for (int i = 0; i < urls.size(); i++) {
            ProductImage image = new ProductImage();
            image.setProduct(product);
            image.setUrl(urls.get(i));
            image.setSortOrder(sortOrderStart + i);
            product.getImages().add(image);
        }
        productRepository.save(product);
        return urls;
    }

    private String generateSku() {
        String sku;
        int attempts = 0;
        do {
            sku = "LCY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
            attempts++;
        } while (productRepository.existsBySku(sku) && attempts < 5);
        return sku;
    }
}
