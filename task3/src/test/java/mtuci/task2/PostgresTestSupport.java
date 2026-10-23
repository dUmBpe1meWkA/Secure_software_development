package mtuci.task2;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;


abstract class PostgresTestSupport {
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("parking_test")
            .withUsername("postgres")
            .withPassword("test-admin");
    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("PARKING_DB_URL", POSTGRES::getJdbcUrl);
        registry.add("PARKING_ADMIN_USER", POSTGRES::getUsername);
        registry.add("PARKING_ADMIN_PASSWORD", POSTGRES::getPassword);
        registry.add("PARKING_APP_PASSWORD", () -> "test-app");
        registry.add("PARKING_SEED", () -> "false");
    }
}
