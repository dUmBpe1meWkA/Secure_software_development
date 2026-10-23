package mtuci.task2.model;

import java.time.Instant;

public record ParkingPass(Long id, Long vehicleId, Long zoneId, Instant validFrom,
                          Instant validUntil, PassStatus status) implements Entity {}
