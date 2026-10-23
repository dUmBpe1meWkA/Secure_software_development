package mtuci.task2.repository;
import mtuci.task2.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ParkingPassRepository extends JdbcRepository<ParkingPass> {
    public ParkingPassRepository(JdbcTemplate jdbc) {
        super(jdbc, "parking_passes", "vehicle_id,zone_id,valid_from,valid_until,status",
            (rs, n) -> new ParkingPass(rs.getLong("id"), rs.getLong("vehicle_id"), rs.getLong("zone_id"), instant(rs, "valid_from"), instant(rs, "valid_until"), PassStatus.valueOf(rs.getString("status"))),
            e -> new Object[]{e.vehicleId(), e.zoneId(), (e.validFrom() == null ? null : e.validFrom().atOffset(java.time.ZoneOffset.UTC)), (e.validUntil() == null ? null : e.validUntil().atOffset(java.time.ZoneOffset.UTC)), e.status().name()});
    }

    private static java.time.Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        var value = rs.getObject(column, java.time.OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
