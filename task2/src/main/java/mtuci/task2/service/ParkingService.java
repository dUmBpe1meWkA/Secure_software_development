package mtuci.task2.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import mtuci.task2.dto.Requests.*;
import mtuci.task2.exception.ApiException;
import mtuci.task2.model.*;
import mtuci.task2.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Owns business rules and referential integrity. The shared monitor makes the
 * capacity check and save atomic, and prevents races between cascades and writes.
 */
@Service
public class ParkingService {
    private final UserRepository users;
    private final VehicleRepository vehicles;
    private final ParkingZoneRepository zones;
    private final ParkingSessionRepository sessions;
    private final ParkingPassRepository passes;
    private final BillingService billing;
    private final Clock clock;

    public ParkingService(UserRepository users, VehicleRepository vehicles, ParkingZoneRepository zones,
            ParkingSessionRepository sessions, ParkingPassRepository passes, BillingService billing, Clock clock) {
        this.users = users;
        this.vehicles = vehicles;
        this.zones = zones;
        this.sessions = sessions;
        this.passes = passes;
        this.billing = billing;
        this.clock = clock;
    }

    public synchronized List<User> listUsers() { return users.findAll(); }
    public synchronized List<Vehicle> listVehicles() { return vehicles.findAll(); }
    public synchronized List<ParkingZone> listZones() { return zones.findAll(); }
    public synchronized List<ParkingSession> listSessions() { return sessions.findAll(); }
    public synchronized List<ParkingPass> listPasses() { return passes.findAll(); }

    public synchronized User getUser(Long id) { return require(users, id, "Пользователь"); }
    public synchronized Vehicle getVehicle(Long id) { return require(vehicles, id, "Автомобиль"); }
    public synchronized ParkingZone getZone(Long id) { return require(zones, id, "Парковочная зона"); }
    public synchronized ParkingSession getSession(Long id) { return require(sessions, id, "Парковочная сессия"); }
    public synchronized ParkingPass getPass(Long id) { return require(passes, id, "Абонемент"); }

    public synchronized User createUser(UserRequest request) {
        String name = text(request.name(), "name", 100);
        String email = email(request.email());
        uniqueEmail(email, null);
        return users.save(new User(users.nextId(), name, email));
    }

    public synchronized User updateUser(Long id, UserRequest request) {
        User old = getUser(id);
        changed(request.name(), request.email());
        String name = request.name() == null ? old.name() : text(request.name(), "name", 100);
        String email = request.email() == null ? old.email() : email(request.email());
        uniqueEmail(email, id);
        return users.save(new User(id, name, email));
    }

    public synchronized void deleteUser(Long id) {
        getUser(id);
        vehicles.findAll().stream().filter(vehicle -> vehicle.ownerId().equals(id))
                .forEach(vehicle -> deleteVehicle(vehicle.id()));
        users.deleteById(id);
    }

    public synchronized Vehicle createVehicle(VehicleRequest request) {
        String plate = plate(request.plateNumber());
        getUser(request.ownerId());
        uniquePlate(plate, null);
        return vehicles.save(new Vehicle(vehicles.nextId(), plate, request.ownerId()));
    }

    public synchronized Vehicle updateVehicle(Long id, VehicleRequest request) {
        Vehicle old = getVehicle(id);
        changed(request.plateNumber(), request.ownerId());
        String plate = request.plateNumber() == null ? old.plateNumber() : plate(request.plateNumber());
        Long ownerId = request.ownerId() == null ? old.ownerId() : request.ownerId();
        getUser(ownerId);
        uniquePlate(plate, id);
        return vehicles.save(new Vehicle(id, plate, ownerId));
    }

    public synchronized void deleteVehicle(Long id) {
        getVehicle(id);
        sessions.findAll().stream().filter(session -> session.vehicleId().equals(id))
                .forEach(session -> sessions.deleteById(session.id()));
        passes.findAll().stream().filter(pass -> pass.vehicleId().equals(id))
                .forEach(pass -> passes.deleteById(pass.id()));
        vehicles.deleteById(id);
    }

    public synchronized ParkingZone createZone(ZoneRequest request) {
        String name = text(request.name(), "name", 100);
        int capacity = capacity(request.capacity());
        BigDecimal price = price(request.hourlyPrice());
        return zones.save(new ParkingZone(zones.nextId(), name, capacity, price));
    }

    public synchronized ParkingZone updateZone(Long id, ZoneRequest request) {
        ParkingZone old = getZone(id);
        changed(request.name(), request.capacity(), request.hourlyPrice());
        String name = request.name() == null ? old.name() : text(request.name(), "name", 100);
        int capacity = request.capacity() == null ? old.capacity() : capacity(request.capacity());
        BigDecimal price = request.hourlyPrice() == null ? old.hourlyPrice() : price(request.hourlyPrice());
        if (occupied(id, null) > capacity) {
            throw ApiException.conflict("Вместимость не может быть меньше числа припаркованных автомобилей");
        }
        return zones.save(new ParkingZone(id, name, capacity, price));
    }

    public synchronized void deleteZone(Long id) {
        getZone(id);
        sessions.findAll().stream().filter(session -> session.zoneId().equals(id))
                .forEach(session -> sessions.deleteById(session.id()));
        passes.findAll().stream().filter(pass -> pass.zoneId().equals(id))
                .forEach(pass -> passes.deleteById(pass.id()));
        zones.deleteById(id);
    }

    public synchronized ParkingSession createSession(SessionCreate request) {
        getVehicle(request.vehicleId());
        ParkingZone zone = getZone(request.zoneId());
        checkSpaceAndVehicle(request.vehicleId(), zone, null);
        return sessions.save(new ParkingSession(sessions.nextId(), request.vehicleId(), request.zoneId(),
                clock.instant(), null, BigDecimal.ZERO.setScale(2), zone.hourlyPrice()));
    }

    public synchronized ParkingSession updateSession(Long id, SessionPatch request) {
        ParkingSession old = getSession(id);
        changed(request.vehicleId(), request.zoneId(), request.close());
        if (!old.isOpen()) {
            throw ApiException.conflict("Завершённая сессия не может быть изменена или открыта повторно");
        }
        if (Boolean.FALSE.equals(request.close())) {
            throw ApiException.badRequest("Для завершения сессии передайте close: true");
        }
        Long vehicleId = request.vehicleId() == null ? old.vehicleId() : request.vehicleId();
        Long zoneId = request.zoneId() == null ? old.zoneId() : request.zoneId();
        if (Boolean.TRUE.equals(request.close()) &&
                (!vehicleId.equals(old.vehicleId()) || !zoneId.equals(old.zoneId()))) {
            throw ApiException.badRequest("Нельзя менять автомобиль или зону одновременно с завершением сессии");
        }
        getVehicle(vehicleId);
        ParkingZone zone = getZone(zoneId);
        checkSpaceAndVehicle(vehicleId, zone, id);
        BigDecimal hourlyPrice = zoneId.equals(old.zoneId()) ? old.hourlyPrice() : zone.hourlyPrice();
        ParkingSession updated = new ParkingSession(id, vehicleId, zoneId, old.entryTime(), null,
                BigDecimal.ZERO.setScale(2), hourlyPrice);
        if (Boolean.TRUE.equals(request.close())) {
            Instant exitTime = clock.instant();
            if (exitTime.isBefore(old.entryTime())) {
                throw ApiException.conflict("Серверное время меньше времени въезда; повторите запрос позже");
            }
            updated = new ParkingSession(id, vehicleId, zoneId, old.entryTime(), exitTime,
                    billing.calculate(updated, exitTime, passes.findAll()), hourlyPrice);
        }
        return sessions.save(updated);
    }

    public synchronized void deleteSession(Long id) {
        getSession(id);
        sessions.deleteById(id);
    }

    public synchronized ParkingPass createPass(PassRequest request) {
        getVehicle(request.vehicleId());
        getZone(request.zoneId());
        period(request.validFrom(), request.validUntil());
        PassStatus status = request.status() == null ? PassStatus.ACTIVE : request.status();
        return passes.save(new ParkingPass(passes.nextId(), request.vehicleId(), request.zoneId(),
                request.validFrom(), request.validUntil(), status));
    }

    public synchronized ParkingPass updatePass(Long id, PassRequest request) {
        ParkingPass old = getPass(id);
        changed(request.vehicleId(), request.zoneId(), request.validFrom(), request.validUntil(), request.status());
        Long vehicleId = request.vehicleId() == null ? old.vehicleId() : request.vehicleId();
        Long zoneId = request.zoneId() == null ? old.zoneId() : request.zoneId();
        Instant from = request.validFrom() == null ? old.validFrom() : request.validFrom();
        Instant until = request.validUntil() == null ? old.validUntil() : request.validUntil();
        PassStatus status = request.status() == null ? old.status() : request.status();
        getVehicle(vehicleId);
        getZone(zoneId);
        period(from, until);
        return passes.save(new ParkingPass(id, vehicleId, zoneId, from, until, status));
    }

    public synchronized void deletePass(Long id) {
        getPass(id);
        passes.deleteById(id);
    }

    private <T extends Entity> T require(InMemoryRepository<T> repository, Long id, String name) {
        if (id == null || id <= 0) {
            throw ApiException.badRequest("id должен быть положительным целым числом");
        }
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, name + " с id=" + id + " не найден"));
    }

    private void changed(Object... fields) {
        for (Object field : fields) {
            if (field != null) { return; }
        }
        throw ApiException.badRequest("PATCH должен содержать хотя бы одно изменяемое поле");
    }

    private String text(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.strip().length() > maxLength) {
            throw ApiException.badRequest(field + " обязателен; длина от 1 до " + maxLength);
        }
        return value.strip();
    }

    private String email(String value) {
        String normalized = text(value, "email", 254).toLowerCase(Locale.ROOT);
        if (!normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw ApiException.badRequest("email должен быть корректным адресом электронной почты");
        }
        return normalized;
    }

    private String plate(String value) {
        String normalized = text(value, "plateNumber", 15).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[\\p{L}\\p{N}-]{3,15}")) {
            throw ApiException.badRequest("plateNumber: от 3 до 15 букв, цифр или дефисов");
        }
        return normalized;
    }

    private int capacity(Integer value) {
        if (value == null || value < 1 || value > 100_000) {
            throw ApiException.badRequest("capacity должен быть целым числом от 1 до 100000");
        }
        return value;
    }

    private BigDecimal price(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.valueOf(1_000_000)) > 0) {
            throw ApiException.badRequest("hourlyPrice должен быть от 0 до 1000000");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw ApiException.badRequest("hourlyPrice должен содержать не более двух значащих знаков после запятой");
        }
    }

    private void period(Instant from, Instant until) {
        if (from == null || until == null || !from.isBefore(until)) {
            throw ApiException.badRequest("validFrom и validUntil обязательны; validFrom должен быть раньше validUntil");
        }
    }

    private void uniqueEmail(String email, Long exceptId) {
        if (users.findAll().stream().anyMatch(user -> !user.id().equals(exceptId) && user.email().equals(email))) {
            throw ApiException.conflict("Пользователь с таким email уже существует");
        }
    }

    private void uniquePlate(String plate, Long exceptId) {
        if (vehicles.findAll().stream().anyMatch(vehicle -> !vehicle.id().equals(exceptId) && vehicle.plateNumber().equals(plate))) {
            throw ApiException.conflict("Автомобиль с таким номером уже существует");
        }
    }

    private long occupied(Long zoneId, Long exceptSessionId) {
        return sessions.findAll().stream().filter(ParkingSession::isOpen)
                .filter(session -> !session.id().equals(exceptSessionId) && session.zoneId().equals(zoneId)).count();
    }

    private void checkSpaceAndVehicle(Long vehicleId, ParkingZone zone, Long exceptSessionId) {
        if (sessions.findAll().stream().filter(ParkingSession::isOpen)
                .anyMatch(session -> !session.id().equals(exceptSessionId) && session.vehicleId().equals(vehicleId))) {
            throw ApiException.conflict("У автомобиля уже есть незавершённая парковочная сессия");
        }
        if (occupied(zone.id(), exceptSessionId) >= zone.capacity()) {
            throw ApiException.conflict("В парковочной зоне нет свободных мест");
        }
    }
}
