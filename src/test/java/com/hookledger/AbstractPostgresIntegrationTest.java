package com.hookledger;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class AbstractPostgresIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("hookledger")
                .withUsername("hookledger")
                .withPassword("hookledger");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add(
                "hookledger.webhook.secret",
                () -> {
                    String fromEnv = System.getenv("HOOKLEDGER_WEBHOOK_SECRET");
                    return fromEnv != null && !fromEnv.isBlank() ? fromEnv : "integration-test-secret";
                });
    }
}
