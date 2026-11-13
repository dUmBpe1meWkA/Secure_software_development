package mtuci.task2.model;

import java.math.BigDecimal;

public record ParkingZone(Long id, String name, Integer capacity, BigDecimal hourlyPrice) implements Entity {}
