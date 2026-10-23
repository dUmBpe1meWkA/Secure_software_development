package mtuci.task2.repository;
import mtuci.task2.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ParkingSessionRepository extends JdbcRepository<ParkingSession> {
    public ParkingSessionRepository(JdbcTemplate jdbc) {
        super(jdbc, "parking_sessions", "vehicle_id,zone_id,entry_time,exit_time,amount,hourly_price",
            (rs, n) -> new ParkingSession(rs.getLong("id"), rs.getLong("vehicle_id"), rs.getLong("zone_id"), instant(rs, "entry_time"), instant(rs, "exit_time"), rs.getBigDecimal("amount"), rs.getBigDecimal("hourly_price")),
            e -> new Object[]{e.vehicleId(), e.zoneId(), (e.entryTime() == null ? null : e.entryTime().atOffset(java.time.ZoneOffset.UTC)), (e.exitTime() == null ? null : e.exitTime().atOffset(java.time.ZoneOffset.UTC)), e.amount(), e.hourlyPrice()});
    }

    private static java.time.Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        var value = rs.getObject(column, java.time.OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
