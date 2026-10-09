package mtuci.task2;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import mtuci.task2.dto.Requests.SessionCreate;
import mtuci.task2.dto.Requests.VehicleRequest;
import mtuci.task2.exception.ApiException;
import mtuci.task2.service.ParkingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ParkingApiTests.TimeConfiguration.class)
class ParkingApiTests {
    private static final Instant START = Instant.parse("2026-10-09T08:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ParkingService service;
    @Autowired MutableClock clock;
    private long userId;
    private long vehicleId;
    private long zoneId;

    @BeforeEach
    void prepare() throws Exception {
        service.listUsers().forEach(user -> service.deleteUser(user.id()));
        service.listZones().forEach(zone -> service.deleteZone(zone.id()));
        clock.now = START;
        userId = create("users", "{\"name\":\"Иван\",\"email\":\"ivan@example.com\"}");
        vehicleId = create("vehicles", "{\"plateNumber\":\"А123АА77\",\"ownerId\":%d}".formatted(userId));
        zoneId = create("parking-zones", "{\"name\":\"Центр\",\"capacity\":2,\"hourlyPrice\":120}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"users", "vehicles", "parking-zones", "parking-sessions", "parking-passes"})
    void allFiveOperationsForEveryResource(String resource) throws Exception {
        String body = switch (resource) {
            case "users" -> "{\"name\":\"Анна\",\"email\":\"anna@example.com\"}";
            case "vehicles" -> "{\"plateNumber\":\"В456ВВ77\",\"ownerId\":%d}".formatted(userId);
            case "parking-zones" -> "{\"name\":\"Юг\",\"capacity\":4,\"hourlyPrice\":80}";
            case "parking-sessions" -> sessionBody(vehicleId, zoneId);
            case "parking-passes" -> passBody(vehicleId, zoneId, START, START.plus(Duration.ofDays(30)), "ACTIVE");
            default -> throw new IllegalArgumentException(resource);
        };
        long id = create(resource, body);
        mvc.perform(get("/api/" + resource)).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        mvc.perform(get("/api/" + resource + "/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        String patchBody = switch (resource) {
            case "users", "parking-zones" -> "{\"name\":\"Новое имя\"}";
            case "vehicles" -> "{\"plateNumber\":\"С789СС77\"}";
            case "parking-sessions" -> "{\"close\":true}";
            case "parking-passes" -> "{\"status\":\"CANCELLED\"}";
            default -> throw new IllegalArgumentException(resource);
        };
        var updated = patch(resource, id, patchBody).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        switch (resource) {
            case "users", "parking-zones" -> updated.andExpect(jsonPath("$.name").value("Новое имя"));
            case "vehicles" -> updated.andExpect(jsonPath("$.plateNumber").value("С789СС77"))
                    .andExpect(jsonPath("$.ownerId").value(userId));
            case "parking-sessions" -> updated.andExpect(jsonPath("$.exitTime").value(START.toString()));
            case "parking-passes" -> updated.andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.vehicleId").value(vehicleId));
            default -> throw new IllegalArgumentException(resource);
        }
        mvc.perform(delete("/api/" + resource + "/" + id)).andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get("/api/" + resource + "/" + id)).andExpect(status().isNotFound());
        JsonNode list = mapper.readTree(mvc.perform(get("/api/" + resource)).andReturn().getResponse().getContentAsString());
        for (JsonNode item : list) {
            assertThat(item.get("id").asLong()).isNotEqualTo(id);
        }
    }

    @Test
    void serverTimesAndPriceSnapshotAreUsed() throws Exception {
        long id = create("parking-sessions", sessionBody(vehicleId, zoneId));
        mvc.perform(get("/api/parking-sessions/" + id)).andExpect(jsonPath("$.entryTime").value(START.toString()));
        patch("parking-zones", zoneId, "{\"hourlyPrice\":999}").andExpect(status().isOk());
        clock.now = START.plusSeconds(5400);
        patch("parking-sessions", id, "{\"close\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.exitTime").value(clock.now.toString()))
                .andExpect(jsonPath("$.amount").value(180.0))
                .andExpect(jsonPath("$.hourlyPrice").value(120.0));
        patch("parking-sessions", id, "{\"close\":true}").andExpect(status().isConflict());
        create("parking-sessions", sessionBody(vehicleId, zoneId));
    }

    @Test
    void passCoverageIsMergedAndCancelledPassIsIgnored() throws Exception {
        create("parking-passes", passBody(vehicleId, zoneId, START, START.plusSeconds(3600), "ACTIVE"));
        create("parking-passes", passBody(vehicleId, zoneId, START.plusSeconds(1800), START.plusSeconds(5400), "ACTIVE"));
        create("parking-passes", passBody(vehicleId, zoneId, START, START.plusSeconds(7200), "CANCELLED"));
        long id = create("parking-sessions", sessionBody(vehicleId, zoneId));
        clock.now = START.plusSeconds(7200);
        patch("parking-sessions", id, "{\"close\":true}").andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(60.0));
    }

    @Test
    void fullPassMakesSessionFree() throws Exception {
        create("parking-passes", passBody(vehicleId, zoneId, START.minusSeconds(1), START.plusSeconds(3600), "ACTIVE"));
        long id = create("parking-sessions", sessionBody(vehicleId, zoneId));
        clock.now = START.plusSeconds(3600);
        patch("parking-sessions", id, "{\"close\":true}").andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(0.0));
    }

    @Test
    void disjointPassPeriodsAreClippedToSession() throws Exception {
        create("parking-passes", passBody(vehicleId, zoneId, START.minusSeconds(3600), START.plusSeconds(3600), "ACTIVE"));
        create("parking-passes", passBody(vehicleId, zoneId, START.plusSeconds(7200), START.plusSeconds(14400), "ACTIVE"));
        long id = create("parking-sessions", sessionBody(vehicleId, zoneId));
        clock.now = START.plusSeconds(10800);
        patch("parking-sessions", id, "{\"close\":true}").andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(120.0));
    }

    @Test
    void fractionalSecondsAreRoundedToKopecks() throws Exception {
        long id = create("parking-sessions", sessionBody(vehicleId, zoneId));
        clock.now = START.plusNanos(150_000_000);
        patch("parking-sessions", id, "{\"close\":true}").andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(0.01));
    }

    @Test
    void passStatusDefaultsToActiveAndPartialPatchPreservesPeriod() throws Exception {
        long id = create("parking-passes", "{\"vehicleId\":%d,\"zoneId\":%d,\"validFrom\":\"%s\",\"validUntil\":\"%s\"}"
                .formatted(vehicleId, zoneId, START, START.plusSeconds(3600)));
        mvc.perform(get("/api/parking-passes/" + id)).andExpect(jsonPath("$.status").value("ACTIVE"));
        patch("parking-passes", id, "{\"status\":\"CANCELLED\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.validFrom").value(START.toString()))
                .andExpect(jsonPath("$.validUntil").value(START.plusSeconds(3600).toString()));
    }

    @Test
    void unrelatedAndExpiredPassesDoNotCoverSession() throws Exception {
        long otherZone = create("parking-zones", "{\"name\":\"Другая\",\"capacity\":1,\"hourlyPrice\":100}");
        long otherVehicle = create("vehicles", "{\"plateNumber\":\"В222ВВ77\",\"ownerId\":%d}".formatted(userId));
        create("parking-passes", passBody(vehicleId, otherZone, START, START.plusSeconds(7200), "ACTIVE"));
        create("parking-passes", passBody(otherVehicle, zoneId, START, START.plusSeconds(7200), "ACTIVE"));
        create("parking-passes", passBody(vehicleId, zoneId, START.minusSeconds(7200), START, "ACTIVE"));
        long id = create("parking-sessions", sessionBody(vehicleId, zoneId));
        clock.now = START.plusSeconds(3600);
        patch("parking-sessions", id, "{\"close\":true}").andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(120.0));
    }

    @Test
    void capacityAndOneOpenSessionAreEnforced() throws Exception {
        patch("parking-zones", zoneId, "{\"capacity\":1}").andExpect(status().isOk());
        long first = create("parking-sessions", sessionBody(vehicleId, zoneId));
        postBody("parking-sessions", sessionBody(vehicleId, zoneId)).andExpect(status().isConflict());
        long secondVehicle = create("vehicles", "{\"plateNumber\":\"В222ВВ77\",\"ownerId\":%d}".formatted(userId));
        postBody("parking-sessions", sessionBody(secondVehicle, zoneId)).andExpect(status().isConflict());
        patch("parking-zones", zoneId, "{\"capacity\":2}").andExpect(status().isOk());
        create("parking-sessions", sessionBody(secondVehicle, zoneId));
        patch("parking-zones", zoneId, "{\"capacity\":1}").andExpect(status().isConflict());
        mvc.perform(delete("/api/parking-sessions/" + first)).andExpect(status().isNoContent());
        create("parking-sessions", sessionBody(vehicleId, zoneId));
    }

    @Test
    void openSessionCanBeCorrectedWithoutChangingEntryTime() throws Exception {
        long id = create("parking-sessions", sessionBody(vehicleId, zoneId));
        long otherZone = create("parking-zones", "{\"name\":\"Другая\",\"capacity\":1,\"hourlyPrice\":60}");
        clock.now = START.plusSeconds(60);
        patch("parking-sessions", id, "{\"zoneId\":%d}".formatted(otherZone)).andExpect(status().isOk())
                .andExpect(jsonPath("$.zoneId").value(otherZone)).andExpect(jsonPath("$.entryTime").value(START.toString()));
        clock.now = START.plusSeconds(3600);
        patch("parking-sessions", id, "{\"close\":true}").andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(60.0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"users", "vehicles", "parking-zones"})
    void deletionCascadesWithoutDanglingReferences(String resource) throws Exception {
        long sessionId = create("parking-sessions", sessionBody(vehicleId, zoneId));
        long passId = create("parking-passes", passBody(vehicleId, zoneId, START, START.plusSeconds(3600), "ACTIVE"));
        long parentId = switch (resource) {
            case "users" -> userId;
            case "vehicles" -> vehicleId;
            default -> zoneId;
        };
        mvc.perform(delete("/api/" + resource + "/" + parentId)).andExpect(status().isNoContent());
        mvc.perform(get("/api/parking-sessions/" + sessionId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/parking-passes/" + passId)).andExpect(status().isNotFound());
        assertThat(service.listSessions()).isEmpty();
        assertThat(service.listPasses()).isEmpty();
        if (resource.equals("parking-zones")) {
            assertThat(service.getVehicle(vehicleId).ownerId()).isEqualTo(userId);
        } else {
            assertThat(service.listVehicles()).isEmpty();
        }
    }

    @Test
    void clientCannotSupplyServerOwnedFields() throws Exception {
        long id = create("parking-sessions", sessionBody(vehicleId, zoneId));
        for (String field : List.of("entryTime", "exitTime", "amount", "hourlyPrice", "currentTime", "id")) {
            String body = "{\"vehicleId\":%d,\"zoneId\":%d,\"%s\":\"2020-01-01T00:00:00Z\"}"
                    .formatted(vehicleId, zoneId, field);
            postBody("parking-sessions", body).andExpect(status().isBadRequest());
            patch("parking-sessions", id, "{\"close\":true,\"%s\":0}".formatted(field)).andExpect(status().isBadRequest());
        }
        assertThat(service.getSession(id).isOpen()).isTrue();
    }

    @Test
    void invalidInputAndReferencesHaveStructuredErrors() throws Exception {
        postBody("users", "{}").andExpect(status().isBadRequest());
        postBody("users", "{\"name\":\"X\",\"email\":\"bad\"}").andExpect(status().isBadRequest());
        postBody("vehicles", "{\"plateNumber\":\"А999АА77\",\"ownerId\":999999}").andExpect(status().isNotFound());
        postBody("parking-sessions", "{\"vehicleId\":%d,\"zoneId\":999999}".formatted(vehicleId)).andExpect(status().isNotFound());
        postBody("parking-zones", "{\"name\":\"X\",\"capacity\":0,\"hourlyPrice\":10}").andExpect(status().isBadRequest());
        patch("parking-zones", zoneId, "{\"hourlyPrice\":-1}").andExpect(status().isBadRequest());
        patch("parking-zones", zoneId, "{\"hourlyPrice\":1.001}").andExpect(status().isBadRequest());
        patch("parking-zones", zoneId, "{\"capacity\":1.5}").andExpect(status().isBadRequest());
        patch("vehicles", vehicleId, "{\"ownerId\":999999}").andExpect(status().isNotFound());
        assertThat(service.getVehicle(vehicleId).ownerId()).isEqualTo(userId);
        postBody("parking-passes", passBody(vehicleId, zoneId, START, START, "ACTIVE")).andExpect(status().isBadRequest());
        postBody("parking-passes", passBody(vehicleId, zoneId, START, START.plusSeconds(1), "INVALID")).andExpect(status().isBadRequest());
        patch("users", userId, "{}").andExpect(status().isBadRequest());
        patch("users", userId, "{\"name\":null}").andExpect(status().isBadRequest());
        postBody("users", "{broken").andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404)).andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.path").value("/api/users/999999"))
                .andExpect(jsonPath("$.timestamp").value(START.toString()));
        mvc.perform(put("/api/users/" + userId)).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/users").contentType(MediaType.TEXT_PLAIN).content("abc")).andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void duplicatesAreRejectedAfterNormalization() throws Exception {
        postBody("users", "{\"name\":\"Другой\",\"email\":\"IVAN@example.com\"}").andExpect(status().isConflict());
        postBody("vehicles", "{\"plateNumber\":\"а123аа77\",\"ownerId\":%d}".formatted(userId)).andExpect(status().isConflict());
    }

    @Test
    void missingResourceRejectsPatchAndDeleteForEveryEntity() throws Exception {
        for (String resource : List.of("users", "vehicles", "parking-zones", "parking-sessions", "parking-passes")) {
            patch(resource, 999999, "{}").andExpect(status().isNotFound());
            mvc.perform(delete("/api/" + resource + "/999999")).andExpect(status().isNotFound());
        }
    }

    @Test
    void concurrentArrivalsCannotOverfillZone() throws Exception {
        patch("parking-zones", zoneId, "{\"capacity\":1}").andExpect(status().isOk());
        long secondVehicle = service.createVehicle(new VehicleRequest("В222ВВ77", userId)).id();
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> first = arrival(barrier, vehicleId);
            Callable<Integer> second = arrival(barrier, secondVehicle);
            var results = executor.invokeAll(List.of(first, second));
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(service.listSessions()).hasSize(1);
    }

    @Test
    void backwardsServerClockDoesNotProduceNegativeCharge() throws Exception {
        long id = create("parking-sessions", sessionBody(vehicleId, zoneId));
        clock.now = START.minusSeconds(1);
        patch("parking-sessions", id, "{\"close\":true}").andExpect(status().isConflict());
        assertThat(service.getSession(id).isOpen()).isTrue();
        assertThat(service.getSession(id).amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private Callable<Integer> arrival(CyclicBarrier barrier, long id) {
        return () -> {
            barrier.await();
            try {
                service.createSession(new SessionCreate(id, zoneId));
                return 201;
            } catch (ApiException exception) {
                return exception.status().value();
            }
        };
    }

    private long create(String resource, String body) throws Exception {
        var response = postBody(resource, body).andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNumber())
                .andReturn().getResponse();
        long id = mapper.readTree(response.getContentAsString()).get("id").asLong();
        assertThat(response.getHeader("Location")).isEqualTo("/api/" + resource + "/" + id);
        return id;
    }

    private ResultActions postBody(String resource, String body) throws Exception {
        return mvc.perform(post("/api/" + resource).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions patch(String resource, long id, String body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/" + resource + "/" + id)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String sessionBody(long vehicle, long zone) {
        return "{\"vehicleId\":%d,\"zoneId\":%d}".formatted(vehicle, zone);
    }

    private String passBody(long vehicle, long zone, Instant from, Instant until, String status) {
        return "{\"vehicleId\":%d,\"zoneId\":%d,\"validFrom\":\"%s\",\"validUntil\":\"%s\",\"status\":\"%s\"}"
                .formatted(vehicle, zone, from, until, status);
    }

    @TestConfiguration
    static class TimeConfiguration {
        @Bean
        @Primary
        MutableClock testClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        volatile Instant now = START;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
