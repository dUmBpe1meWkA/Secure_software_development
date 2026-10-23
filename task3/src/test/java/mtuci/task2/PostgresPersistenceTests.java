package mtuci.task2;

import java.sql.DriverManager;
import mtuci.task2.config.PostgresConfig;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class PostgresPersistenceTests extends PostgresTestSupport {
    @Test void dataAndSeedMarkerSurviveReinitialization() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var sql = connection.createStatement()) {
            sql.execute("CREATE DATABASE parking_seed_test");
        }
        var env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "PARKING_DB_URL", POSTGRES.getJdbcUrl().replace("/parking_test", "/parking_seed_test"),
                "PARKING_ADMIN_USER", POSTGRES.getUsername(),
                "PARKING_ADMIN_PASSWORD", POSTGRES.getPassword(), "PARKING_APP_PASSWORD", "test-app",
                "PARKING_SEED", "true")));
        var config = new PostgresConfig();
        var first = new JdbcTemplate(config.dataSource(env));
        assertThat(first.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class)).isEqualTo(2);
        first.update("UPDATE app_users SET name='Изменено' WHERE id=1");
        var reopened = new JdbcTemplate(config.dataSource(env));
        assertThat(reopened.queryForObject("SELECT name FROM app_users WHERE id=1", String.class)).isEqualTo("Изменено");
        assertThat(reopened.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class)).isEqualTo(2);
        reopened.update("DELETE FROM app_users");
        var empty = new JdbcTemplate(config.dataSource(env));
        assertThat(empty.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class)).isZero();
    }
}
