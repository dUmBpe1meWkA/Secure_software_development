package mtuci.task2.repository;
import mtuci.task2.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VehicleRepository extends JdbcRepository<Vehicle> {
    public VehicleRepository(JdbcTemplate jdbc) {
        super(jdbc, "vehicles", "plate_number,owner_id",
            (rs, n) -> new Vehicle(rs.getLong("id"), rs.getString("plate_number"), rs.getLong("owner_id")),
            e -> new Object[]{e.plateNumber(), e.ownerId()});
    }
}
