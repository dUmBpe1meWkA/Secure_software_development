package mtuci.task2;

import java.math.BigDecimal;
import java.time.*;
import javax.sql.DataSource;
import mtuci.task2.dto.Requests.*;
import mtuci.task2.exception.ApiException;
import mtuci.task2.model.*;
import mtuci.task2.service.ParkingService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Import(ParkingApiTests.TimeConfiguration.class)
class DatabaseOperationsTests extends PostgresTestSupport {
    @Autowired ParkingService service;
    @Autowired ParkingApiTests.MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    long vehicle, zone, target;
    Instant start = Instant.parse("2026-10-22T08:00:00Z");

    @BeforeEach void setup() {
        service.listUsers().forEach(u -> service.deleteUser(u.id()));
        service.listZones().forEach(z -> service.deleteZone(z.id()));
        clock.now = start;
        long owner = service.createUser(new UserRequest("Иван", "test@example.com")).id();
        vehicle = service.createVehicle(new VehicleRequest("ABC123", owner)).id();
        zone = service.createZone(new ZoneRequest("Центр", 2, new BigDecimal("120"))).id();
        target = service.createZone(new ZoneRequest("Вокзал", 1, new BigDecimal("80"))).id();
    }

    @Test void moveClosesOldSessionAndUsesNewTariff() {
        var old = service.createSession(new SessionCreate(vehicle, zone));
        clock.now = start.plusSeconds(3600);
        var result = service.moveSession(old.id(), target);
        assertThat(result.get(0).amount()).isEqualByComparingTo("120");
        assertThat(result.get(0).isOpen()).isFalse();
        assertThat(result.get(1).zoneId()).isEqualTo(target);
        assertThat(result.get(1).hourlyPrice()).isEqualByComparingTo("80");
        assertThat(result.get(1).entryTime()).isEqualTo(clock.now);
        assertThat(service.listSessions().stream().filter(ParkingSession::isOpen).count()).isEqualTo(1);
    }

    @Test void fullDestinationLeavesOriginalSessionOpen() {
        long owner = service.getVehicle(vehicle).ownerId();
        long other = service.createVehicle(new VehicleRequest("DEF456", owner)).id();
        service.createSession(new SessionCreate(other, target));
        var old = service.createSession(new SessionCreate(vehicle, zone));
        assertThatThrownBy(() -> service.moveSession(old.id(), target)).isInstanceOf(ApiException.class);
        assertThat(service.getSession(old.id()).isOpen()).isTrue();
        assertThat(service.listSessions()).hasSize(2);
    }

    @Test void renewChecksStatusAndExpiry() {
        var pass = service.createPass(new PassRequest(vehicle, zone, start, start.plusSeconds(3600), PassStatus.ACTIVE));
        var renewed = service.renewPass(pass.id(), 7);
        assertThat(renewed.validUntil()).isEqualTo(pass.validUntil().plus(Duration.ofDays(7)));
        assertThatThrownBy(() -> service.renewPass(pass.id(), 0)).isInstanceOf(ApiException.class);
        service.updatePass(pass.id(), new PassRequest(null, null, null, null, PassStatus.CANCELLED));
        assertThatThrownBy(() -> service.renewPass(pass.id(), 1)).isInstanceOf(ApiException.class);
        var expired = service.createPass(new PassRequest(vehicle, zone, start.minusSeconds(3600), start, PassStatus.ACTIVE));
        assertThatThrownBy(() -> service.renewPass(expired.id(), 1)).isInstanceOf(ApiException.class);
    }

    @Test void clearingZoneBillsAllCarsAndRejectsEmptyZone() {
        service.createSession(new SessionCreate(vehicle, zone));
        long other = service.createVehicle(new VehicleRequest("DEF456", service.getVehicle(vehicle).ownerId())).id();
        service.createSession(new SessionCreate(other, zone));
        clock.now = start.plusSeconds(1800);
        assertThat(service.clearZone(zone)).allSatisfy(s -> {
            assertThat(s.isOpen()).isFalse();
            assertThat(s.amount()).isEqualByComparingTo("60");
        }).hasSize(2);
        assertThatThrownBy(() -> service.clearZone(zone)).isInstanceOf(ApiException.class);
    }

    @Test void clearingRollsBackEarlierClosuresIfLaterCarCannotExit() {
        var first = service.createSession(new SessionCreate(vehicle, zone));
        clock.now = start.plusSeconds(3600);
        long other = service.createVehicle(new VehicleRequest("DEF456", service.getVehicle(vehicle).ownerId())).id();
        var second = service.createSession(new SessionCreate(other, zone));
        clock.now = start.plusSeconds(1800);
        assertThatThrownBy(() -> service.clearZone(zone)).isInstanceOf(ApiException.class);
        assertThat(service.getSession(first.id()).isOpen()).isTrue();
        assertThat(service.getSession(second.id()).isOpen()).isTrue();
    }

    @Test void runtimeAccountCannotChangeSchema() throws Exception {
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
            assertThat(connection.getMetaData().getUserName()).isEqualToIgnoringCase("parking_app");
            assertThatThrownBy(() -> sql.execute("CREATE TABLE forbidden(id INT)")).isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> sql.execute("DROP TABLE parking_zones")).isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test void databaseEnforcesForeignKeysChecksAndSingleOpenSession() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO vehicles VALUES (90, 'ZZZ999', -1)"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE parking_zones SET capacity=0 WHERE id=?", zone))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        service.createSession(new SessionCreate(vehicle, zone));
        assertThatThrownBy(() -> jdbc.update("INSERT INTO parking_sessions(id,vehicle_id,zone_id,entry_time,amount,hourly_price) VALUES (90,?,?,CURRENT_TIMESTAMP,0,120)", vehicle, zone))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
