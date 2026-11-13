package mtuci.task2.repository;

import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import mtuci.task2.model.SessionStatus;
import mtuci.task2.model.UserSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserSessionRepository {
    private final JdbcTemplate jdbc;

    public UserSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserSession> findForUpdate(String refresh) {
        return jdbc.query("SELECT * FROM user_sessions WHERE refresh_token = ? FOR UPDATE",
                (rs, row) -> new UserSession(rs.getObject("id", UUID.class), rs.getString("username"),
                        rs.getString("device_id"), rs.getString("access_token"), rs.getString("refresh_token"),
                        rs.getTimestamp("access_token_expiry").toInstant(),
                        rs.getTimestamp("refresh_token_expiry").toInstant(),
                        SessionStatus.valueOf(rs.getString("status"))), refresh).stream().findFirst();
    }

    public void save(UserSession session) {
        jdbc.update("""
                INSERT INTO user_sessions(id, username, device_id, access_token, refresh_token,
                    access_token_expiry, refresh_token_expiry, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, session.id(), session.username(), session.deviceId(), session.accessToken(),
                session.refreshToken(), session.accessTokenExpiry().atOffset(ZoneOffset.UTC),
                session.refreshTokenExpiry().atOffset(ZoneOffset.UTC), session.status().name());
    }

    public void setStatus(UUID id, SessionStatus status) {
        jdbc.update("UPDATE user_sessions SET status = ? WHERE id = ?", status.name(), id);
    }
}
