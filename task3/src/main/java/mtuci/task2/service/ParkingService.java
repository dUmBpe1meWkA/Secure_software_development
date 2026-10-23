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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional
public class ParkingService {
    private final UserRepository users;
    private final VehicleRepository vehicles;
    private final ParkingZoneRepository zones;
    private final ParkingSessionRepository sessions;
    private final ParkingPassRepository passes;
    private final BillingService billing;
    private final Clock clock;
    private final JdbcTemplate jdbc;

    public ParkingService(UserRepository users, VehicleRepository vehicles, ParkingZoneRepository zones,
            ParkingSessionRepository sessions, ParkingPassRepository passes, BillingService billing, Clock clock, JdbcTemplate jdbc) {
        this.users = users;
        this.vehicles = vehicles;
        this.zones = zones;
        this.sessions = sessions;
        this.passes = passes;
        this.billing = billing;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    public List<User> listUsers() { lock(); return users.findAll(); }
    public List<Vehicle> listVehicles() { lock(); return vehicles.findAll(); }
    public List<ParkingZone> listZones() { lock(); return zones.findAll(); }
    public List<ParkingSession> listSessions() { lock(); return sessions.findAll(); }
    public List<ParkingPass> listPasses() { lock(); return passes.findAll(); }

    public User getUser(Long id) { lock(); return require(users, id, "Пользователь"); }
    public Vehicle getVehicle(Long id) { lock(); return require(vehicles, id, "Автомобиль"); }
    public ParkingZone getZone(Long id) { lock(); return require(zones, id, "Парковочная зона"); }
    public ParkingSession getSession(Long id) { lock(); return require(sessions, id, "Парковочная сессия"); }
    public ParkingPass getPass(Long id) { lock(); return require(passes, id, "Абонемент"); }

    public User createUser(UserRequest request) { lock();
        String name = text(request.name(), "name", 100);
        String email = email(request.email());
        uniqueEmail(email, null);
        return users.save(new User(users.nextId(), name, email));
    }

    public User updateUser(Long id, UserRequest request) { lock();
        User old = getUser(id);
        changed(request.name(), request.email());
        String name = request.name() == null ? old.name() : text(request.name(), "name", 100);
        String email = request.email() == null ? old.email() : email(request.email());
        uniqueEmail(email, id);
        return users.save(new User(id, name, email));
    }

    public void deleteUser(Long id) { lock();
        getUser(id);
        vehicles.findAll().stream().filter(vehicle -> vehicle.ownerId().equals(id))
                .forEach(vehicle -> deleteVehicle(vehicle.id()));
        users.deleteById(id);
    }

    public Vehicle createVehicle(VehicleRequest request) { lock();
        String plate = plate(request.plateNumber());
        getUser(request.ownerId());
        uniquePlate(plate, null);
        return vehicles.save(new Vehicle(vehicles.nextId(), plate, request.ownerId()));
    }

    public Vehicle updateVehicle(Long id, VehicleRequest request) { lock();
        Vehicle old = getVehicle(id);
        changed(request.plateNumber(), request.ownerId());
        String plate = request.plateNumber() == null ? old.plateNumber() : plate(request.plateNumber());
        Long ownerId = request.ownerId() == null ? old.ownerId() : request.ownerId();
        getUser(ownerId);
        uniquePlate(plate, id);
        return vehicles.save(new Vehicle(id, plate, ownerId));
    }

    public void deleteVehicle(Long id) { lock();
        getVehicle(id);
        sessions.findAll().stream().filter(session -> session.vehicleId().equals(id))
                .forEach(session -> sessions.deleteById(session.id()));
        passes.findAll().stream().filter(pass -> pass.vehicleId().equals(id))
                .forEach(pass -> passes.deleteById(pass.id()));
        vehicles.deleteById(id);
    }

    public ParkingZone createZone(ZoneRequest request) { lock();
        String name = text(request.name(), "name", 100);
        int capacity = capacity(request.capacity());
        BigDecimal price = price(request.hourlyPrice());
        return zones.save(new ParkingZone(zones.nextId(), name, capacity, price));
    }

    public ParkingZone updateZone(Long id, ZoneRequest request) { lock();
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

    public void deleteZone(Long id) { lock();
        getZone(id);
        sessions.findAll().stream().filter(session -> session.zoneId().equals(id))
                .forEach(session -> sessions.deleteById(session.id()));
        passes.findAll().stream().filter(pass -> pass.zoneId().equals(id))
                .forEach(pass -> passes.deleteById(pass.id()));
        zones.deleteById(id);
    }

    public ParkingSession createSession(SessionCreate request) { lock();
        getVehicle(request.vehicleId());
        ParkingZone zone = getZone(request.zoneId());
        checkSpaceAndVehicle(request.vehicleId(), zone, null);
        return sessions.save(new ParkingSession(sessions.nextId(), request.vehicleId(), request.zoneId(),
                clock.instant(), null, BigDecimal.ZERO.setScale(2), zone.hourlyPrice()));
    }

    public ParkingSession updateSession(Long id, SessionPatch request) { lock();
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

    public void deleteSession(Long id) { lock();
        getSession(id);
        sessions.deleteById(id);
    }

    public ParkingPass createPass(PassRequest request) { lock();
        getVehicle(request.vehicleId());
        getZone(request.zoneId());
        period(request.validFrom(), request.validUntil());
        PassStatus status = request.status() == null ? PassStatus.ACTIVE : request.status();
        return passes.save(new ParkingPass(passes.nextId(), request.vehicleId(), request.zoneId(),
                request.validFrom(), request.validUntil(), status));
    }

    public ParkingPass updatePass(Long id, PassRequest request) { lock();
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

    public void deletePass(Long id) { lock();
        getPass(id);
        passes.deleteById(id);
    }



    private void lock() {
        jdbc.queryForObject("SELECT id FROM operation_lock WHERE id=1 FOR UPDATE", Long.class);
    }

    public List<ParkingSession> moveSession(Long id, Long targetZoneId) {
        lock();
        ParkingSession old = getSession(id);
        if (!old.isOpen()) throw ApiException.conflict("Сессия уже завершена");
        if (old.zoneId().equals(targetZoneId)) throw ApiException.conflict("Автомобиль уже находится в этой зоне");
        ParkingZone target = getZone(targetZoneId);
        checkSpaceAndVehicle(old.vehicleId(), target, old.id());
        ParkingSession closed = updateSession(id, new SessionPatch(null, null, true));
        ParkingSession opened = createSession(new SessionCreate(old.vehicleId(), targetZoneId));
        return List.of(closed, opened);
    }

    public ParkingPass renewPass(Long id, Integer days) {
        lock();
        ParkingPass old = getPass(id);
        if (days == null || days < 1 || days > 365) throw ApiException.badRequest("days должен быть от 1 до 365");
        if (old.status() != PassStatus.ACTIVE || !old.validUntil().isAfter(clock.instant()))
            throw ApiException.conflict("Нельзя продлить отменённый или истёкший абонемент");
        Instant until;
        try { until = old.validUntil().plus(java.time.Duration.ofDays(days)); }
        catch (java.time.DateTimeException | ArithmeticException e) { throw ApiException.badRequest("Слишком большая дата окончания"); }
        return passes.save(new ParkingPass(id, old.vehicleId(), old.zoneId(), old.validFrom(), until, old.status()));
    }

    public List<ParkingSession> clearZone(Long id) {
        lock();
        getZone(id);
        List<ParkingSession> open = sessions.findAll().stream()
                .filter(s -> s.isOpen() && s.zoneId().equals(id)).toList();
        if (open.isEmpty()) throw ApiException.conflict("В зоне нет открытых сессий");
        return open.stream().map(s -> updateSession(s.id(), new SessionPatch(null, null, true))).toList();
    }

    private <T extends Entity> T require(JdbcRepository<T> repository, Long id, String name) {
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
