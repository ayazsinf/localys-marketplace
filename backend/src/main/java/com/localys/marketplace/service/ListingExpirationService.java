package com.localys.marketplace.service;

import com.localys.marketplace.model.Product;
import com.localys.marketplace.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ListingExpirationService {
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final int batchSize;

    public ListingExpirationService(
            ProductRepository productRepository,
            ProductService productService,
            @Value("${app.listings.expiration-batch-size:200}") int batchSize
    ) {
        this.productRepository = productRepository;
        this.productService = productService;
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(cron = "${app.listings.expiration-cron:0 15 * * * *}")
    @Transactional
    public void expireListings() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Product> expired = productRepository.findExpiredActiveListings(now, PageRequest.of(0, batchSize));
        expired.forEach(productService::expireProduct);
    }
}
