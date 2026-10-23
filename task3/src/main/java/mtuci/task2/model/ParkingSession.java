package mtuci.task2.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.Instant;

public record ParkingSession(Long id, Long vehicleId, Long zoneId, Instant entryTime,
                             Instant exitTime, BigDecimal amount, BigDecimal hourlyPrice) implements Entity {
    @JsonIgnore
    public boolean isOpen() {
        return exitTime == null;
    }
}
