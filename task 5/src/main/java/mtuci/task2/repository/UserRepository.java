package mtuci.task2.repository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import mtuci.task2.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository extends JdbcRepository<User> {
    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        super(jdbc, "app_users", "name,email,role,status,password_hash",
            (rs, n) -> new User(rs.getLong("id"), rs.getString("name"), rs.getString("email"),
                Role.valueOf(rs.getString("role")), AccountStatus.valueOf(rs.getString("status")), rs.getString("password_hash")),
            e -> new Object[]{e.name(), e.email(), e.role().name(), e.status().name(), e.passwordHash()});
        this.jdbc = jdbc;
    }

    public Optional<User> findByEmail(String email) {
        return findAll().stream().filter(user -> user.email().equals(email)).findFirst();
    }

    public boolean isLocked(String email, Instant now) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE email=? AND locked_until > ?",
                Integer.class, email, now.atOffset(ZoneOffset.UTC));
        return count != null && count > 0;
    }

    /** Failures during an active lock are not counted, so an attacker cannot prolong it. */
    public void recordFailedLogin(String email, int maxAttempts, Instant now, Instant lockUntil) {
        jdbc.update("""
                UPDATE app_users SET
                    locked_until = CASE WHEN failed_attempts + 1 >= ? THEN ? ELSE locked_until END,
                    failed_attempts = CASE WHEN failed_attempts + 1 >= ? THEN 0 ELSE failed_attempts + 1 END
                WHERE email = ? AND (locked_until IS NULL OR locked_until <= ?)""",
                maxAttempts, lockUntil.atOffset(ZoneOffset.UTC), maxAttempts, email, now.atOffset(ZoneOffset.UTC));
    }

    public void clearFailedLogins(String email) {
        jdbc.update("UPDATE app_users SET failed_attempts = 0, locked_until = NULL "
                + "WHERE email = ? AND (failed_attempts <> 0 OR locked_until IS NOT NULL)", email);
    }
}
