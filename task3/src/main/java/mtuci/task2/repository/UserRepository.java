package mtuci.task2.repository;
import mtuci.task2.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository extends JdbcRepository<User> {
    public UserRepository(JdbcTemplate jdbc) {
        super(jdbc, "app_users", "name,email",
            (rs, n) -> new User(rs.getLong("id"), rs.getString("name"), rs.getString("email")),
            e -> new Object[]{e.name(), e.email()});
    }
}
