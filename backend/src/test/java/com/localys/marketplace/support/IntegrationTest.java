package com.localys.marketplace.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.function.Supplier;

/**
 * Boots the full application against a throwaway PostgreSQL, so the Liquibase migrations,
 * row locks and native queries are exercised exactly as in production.
 * The container is shared by every test class and stopped when the JVM exits.
 */
@SpringBootTest
@Import(TestData.class)
@ActiveProfiles("test")
public abstract class IntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected TestData data;

    @Autowired
    private TransactionTemplate transactionTemplate;

    /** Runs a read inside a transaction so lazy associations can be inspected. */
    protected <T> T inTx(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
