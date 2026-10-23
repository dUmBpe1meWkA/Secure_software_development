package mtuci.task2.repository;
import mtuci.task2.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ParkingZoneRepository extends JdbcRepository<ParkingZone> {
    public ParkingZoneRepository(JdbcTemplate jdbc) {
        super(jdbc, "parking_zones", "name,capacity,hourly_price",
            (rs, n) -> new ParkingZone(rs.getLong("id"), rs.getString("name"), rs.getInt("capacity"), rs.getBigDecimal("hourly_price")),
            e -> new Object[]{e.name(), e.capacity(), e.hourlyPrice()});
    }
}
