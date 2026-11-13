package mtuci.task2;

import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** A disposable database; integration tests never connect to the user's parking database. */
abstract class PostgresTestSupport {
    static final String JWT_SECRET = "integration-only-signing-fixture-at-least-thirty-two-bytes";
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("parking_test")
            .withUsername("postgres")
            .withPassword(UUID.randomUUID().toString());
    static final String APP_SECRET = UUID.randomUUID().toString();
    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("PARKING_DB_URL", POSTGRES::getJdbcUrl);
        registry.add("PARKING_ADMIN_USER", POSTGRES::getUsername);
        registry.add("PARKING_ADMIN_PASSWORD", POSTGRES::getPassword);
        registry.add("PARKING_APP_PASSWORD", () -> APP_SECRET);
        registry.add("PARKING_SEED", () -> "false");
        registry.add("PARKING_JWT_SECRET", () -> JWT_SECRET);
    }
}
