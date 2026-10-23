package mtuci.task2.config;

import java.sql.*;
import java.util.Properties;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

@Configuration
public class PostgresConfig {
    @Bean
    public DataSource dataSource(Environment env) throws SQLException {
        String url = env.getProperty("PARKING_DB_URL", "jdbc:postgresql://localhost:55432/parking");
        if (!url.startsWith("jdbc:postgresql:")) throw new IllegalStateException("PARKING_DB_URL должен указывать на PostgreSQL");
        String adminUser = env.getProperty("PARKING_ADMIN_USER", "postgres");
        String admin = required(env, "PARKING_ADMIN_PASSWORD");
        String app = required(env, "PARKING_APP_PASSWORD");

        try (Connection connection = DriverManager.getConnection(url, adminUser, admin)) {
            connection.setAutoCommit(false);
            try (Statement sql = connection.createStatement()) {

                sql.execute("SELECT pg_advisory_xact_lock(683514013)");
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/schema.sql"));
                try (ResultSet roles = sql.executeQuery("SELECT 1 FROM pg_roles WHERE rolname='parking_app'")) {
                    if (!roles.next()) {
                        try (Statement create = connection.createStatement()) {
                            create.execute("CREATE ROLE parking_app LOGIN");
                        }
                    }
                }

                try (PreparedStatement format = connection.prepareStatement(
                        "SELECT format('ALTER ROLE parking_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L', CAST(? AS text))")) {
                    format.setString(1, app);
                    try (ResultSet result = format.executeQuery()) {
                        result.next();
                        sql.execute(result.getString(1));
                    }
                }
                sql.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
                sql.execute("GRANT USAGE ON SCHEMA public TO parking_app");
                sql.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON app_users, vehicles, parking_zones, parking_sessions, parking_passes TO parking_app");
                sql.execute("GRANT SELECT, UPDATE ON operation_lock TO parking_app");
                sql.execute("GRANT USAGE ON SEQUENCE app_users_seq, vehicles_seq, parking_zones_seq, parking_sessions_seq, parking_passes_seq TO parking_app");
                if (env.getProperty("PARKING_SEED", Boolean.class, true)) {
                    boolean empty;
                    try (ResultSet rows = sql.executeQuery("SELECT COUNT(*) FROM seed_history")) {
                        rows.next();
                        empty = rows.getInt(1) == 0;
                    }
                    if (empty) {
                        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/seed.sql"));
                        sql.executeUpdate("INSERT INTO seed_history VALUES (1)");
                    }
                }
                connection.commit();
            } catch (RuntimeException | SQLException e) {
                connection.rollback();
                throw e;
            }
        }
        var source = new DriverManagerDataSource(url, "parking_app", app);
        var properties = new Properties();
        properties.setProperty("connectTimeout", "10");
        properties.setProperty("options", "-c lock_timeout=5000");
        source.setConnectionProperties(properties);
        return source;
    }
    private String required(Environment env, String name) {
        String value = env.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Задайте переменную окружения " + name);
        return value;
    }
}
