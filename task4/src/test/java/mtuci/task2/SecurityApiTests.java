package mtuci.task2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import mtuci.task2.dto.Requests.*;
import mtuci.task2.model.*;
import mtuci.task2.security.AccountAuthenticationProvider;
import mtuci.task2.service.ParkingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(ParkingApiTests.TimeConfiguration.class)
class SecurityApiTests extends PostgresTestSupport {
    private static final String PASSWORD = "Str0ng!Value-1";
    private static final String ADMIN = "boss@example.com";
    private static final String OPERATOR = "guard@example.com";
    private static final String CLIENT = "client@example.com";
    private static final String OTHER = "other@example.com";
    private static final Instant START = Instant.parse("2026-10-31T08:00:00Z");

    @Autowired WebApplicationContext context;
    @Autowired ParkingService service;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ParkingApiTests.MutableClock clock;
    MockMvc mvc;
    long adminId, operatorId, clientId, otherId, vehicle, otherVehicle, zone, secondZone;

    @BeforeEach
    void prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanUp();
        clock.now = START;
        adminId = account("Босс", ADMIN, Role.ADMIN);
        operatorId = account("Охрана", OPERATOR, Role.OPERATOR);
        clientId = account("Клиент", CLIENT, Role.CLIENT);
        otherId = account("Другой", OTHER, Role.CLIENT);
        vehicle = service.createVehicle(new VehicleRequest("A111AA77", clientId)).id();
        otherVehicle = service.createVehicle(new VehicleRequest("B222BB77", otherId)).id();
        zone = service.createZone(new ZoneRequest("Центр", 5, new java.math.BigDecimal("120"))).id();
        secondZone = service.createZone(new ZoneRequest("Вокзал", 5, new java.math.BigDecimal("80"))).id();
    }

    /** Other test classes delete users through the service, which refuses to remove the last administrator. */
    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM app_users");
        jdbc.update("DELETE FROM parking_zones");
    }

    // --- authentication ---

    @Test
    void anonymousRequestGets401InCommonFormat() throws Exception {
        mvc.perform(get("/api/vehicles")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Basic")))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Требуется аутентификация"))
                .andExpect(jsonPath("$.path").value("/api/vehicles"));
    }

    @Test
    void wrongPasswordAndUnknownAccountLookTheSame() throws Exception {
        String wrong = mvc.perform(get("/api/auth/me").with(httpBasic(CLIENT, "Wrong!Value-1")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(get("/api/auth/me").with(httpBasic("nobody@example.com", "Wrong!Value-1")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(wrong).get("message")).isEqualTo(mapper.readTree(unknown).get("message"));
    }

    @Test
    void passwordLongerThanBcryptLimitIsAnOrdinaryFailure() throws Exception {
        mvc.perform(get("/api/auth/me").with(httpBasic(CLIENT, "x".repeat(200)))).andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsProfileWithoutPasswordHash() throws Exception {
        mvc.perform(as(get("/api/auth/me"), CLIENT)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(CLIENT))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("$2"))));
        mvc.perform(as(get("/api/users"), ADMIN)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("assword"))));
    }

    @Test
    void blockedAccountIsRevealedOnlyToWhoKnowsThePassword() throws Exception {
        patch("/api/users/" + clientId, "{\"status\":\"BLOCKED\"}", ADMIN).andExpect(status().isOk());
        mvc.perform(as(get("/api/auth/me"), CLIENT)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("заблокирована администратором")));
        mvc.perform(get("/api/auth/me").with(httpBasic(CLIENT, "Wrong!Value-1"))).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Неверный email или пароль"));
        patch("/api/users/" + clientId, "{\"status\":\"ACTIVE\"}", ADMIN).andExpect(status().isOk());
        mvc.perform(as(get("/api/auth/me"), CLIENT)).andExpect(status().isOk());
    }

    @Test
    void repeatedFailuresLockAccountTemporarily() throws Exception {
        for (int i = 0; i < AccountAuthenticationProvider.MAX_FAILED_LOGINS; i++) {
            mvc.perform(get("/api/auth/me").with(httpBasic(CLIENT, "Wrong!Value-1"))).andExpect(status().isUnauthorized());
        }
        mvc.perform(as(get("/api/auth/me"), CLIENT)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("временно заблокирована")));
        clock.now = START.plus(AccountAuthenticationProvider.LOCK_DURATION).plusSeconds(1);
        mvc.perform(as(get("/api/auth/me"), CLIENT)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT failed_attempts FROM app_users WHERE id=?", Integer.class, clientId)).isZero();
    }

    @Test
    void successfulLoginResetsFailureCounter() throws Exception {
        mvc.perform(get("/api/auth/me").with(httpBasic(CLIENT, "Wrong!Value-1"))).andExpect(status().isUnauthorized());
        mvc.perform(as(get("/api/auth/me"), CLIENT)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT failed_attempts FROM app_users WHERE id=?", Integer.class, clientId)).isZero();
    }

    // --- CSRF ---

    @Test
    void writesWithoutCsrfTokenAreRejectedEvenWithValidCredentials() throws Exception {
        mvc.perform(rawPost("/api/operations/enter").with(httpBasic(ADMIN, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                        .content(session(vehicle))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("CSRF")));
        mvc.perform(rawPost("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        assertThat(service.listSessions()).isEmpty();
    }

    // --- registration ---

    @Test
    void registrationCreatesClientWithBcryptHash() throws Exception {
        register("{\"name\":\"Новый\",\"email\":\"NEW@example.com\",\"password\":\"%s\"}".formatted(PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT password_hash FROM app_users WHERE email='new@example.com'", String.class))
                .startsWith("$2").isNotEqualTo(PASSWORD);
        mvc.perform(get("/api/auth/me").with(httpBasic("new@example.com", PASSWORD))).andExpect(status().isOk());
    }

    @Test
    void registrationCannotRequestPrivileges() throws Exception {
        for (String field : new String[]{"\"role\":\"ADMIN\"", "\"status\":\"ACTIVE\"", "\"id\":1", "\"passwordHash\":\"x\""}) {
            register("{\"name\":\"Новый\",\"email\":\"new@example.com\",\"password\":\"%s\",%s}".formatted(PASSWORD, field))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE email='new@example.com'", Integer.class)).isZero();
    }

    @Test
    void registrationChecksPasswordStrengthAndUniqueness() throws Exception {
        for (String weak : new String[]{"short1!A", "alllowercase1!", "ALLUPPERCASE1!", "NoDigitsHere!!", "NoSpecials123",
                "MyPassword1!", "new-ivan-Pass1!" , "Я1!" + "a".repeat(80)}) {
            register("{\"name\":\"Новый\",\"email\":\"ivan@example.com\",\"password\":\"%s\"}".formatted(weak))
                    .andExpect(status().isBadRequest());
        }
        register("{\"name\":\"Новый\",\"email\":\"new@example.com\"}").andExpect(status().isBadRequest());
        register("{\"name\":\"Новый\",\"email\":\"CLIENT@example.com\",\"password\":\"%s\"}".formatted(PASSWORD))
                .andExpect(status().isConflict());
    }

    @Test
    void passwordChangeRequiresCurrentPasswordAndTakesEffectImmediately() throws Exception {
        String next = "An0ther!Value-2";
        post("/api/auth/change-password", "{\"currentPassword\":\"Wrong!Value-1\",\"newPassword\":\"%s\"}".formatted(next), CLIENT)
                .andExpect(status().isBadRequest());
        post("/api/auth/change-password", "{\"currentPassword\":\"%s\",\"newPassword\":\"weak\"}".formatted(PASSWORD), CLIENT)
                .andExpect(status().isBadRequest());
        post("/api/auth/change-password", "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(PASSWORD, next), CLIENT)
                .andExpect(status().isNoContent());
        mvc.perform(as(get("/api/auth/me"), CLIENT)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").with(httpBasic(CLIENT, next))).andExpect(status().isOk());
    }

    // --- users ---

    @Test
    void usersAreManagedOnlyByAdministrator() throws Exception {
        mvc.perform(as(get("/api/users"), CLIENT)).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/users"), OPERATOR)).andExpect(status().isOk());
        mvc.perform(as(get("/api/users/" + clientId), CLIENT)).andExpect(status().isOk());
        mvc.perform(as(get("/api/users/" + otherId), CLIENT)).andExpect(status().isForbidden());
        String body = "{\"name\":\"Новый\",\"email\":\"new@example.com\",\"password\":\"%s\",\"role\":\"OPERATOR\"}".formatted(PASSWORD);
        post("/api/users", body, OPERATOR).andExpect(status().isForbidden());
        post("/api/users", body, ADMIN).andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("OPERATOR"));
        delete_("/api/users/" + otherId, OPERATOR).andExpect(status().isForbidden());
        delete_("/api/users/" + otherId, CLIENT).andExpect(status().isForbidden());
        delete_("/api/users/" + otherId, ADMIN).andExpect(status().isNoContent());
    }

    @Test
    void clientEditsOwnProfileButCannotEscalate() throws Exception {
        patch("/api/users/" + clientId, "{\"name\":\"Новое имя\"}", CLIENT).andExpect(status().isOk());
        patch("/api/users/" + otherId, "{\"name\":\"Взлом\"}", CLIENT).andExpect(status().isForbidden());
        patch("/api/users/" + clientId, "{\"role\":\"ADMIN\"}", CLIENT).andExpect(status().isForbidden());
        patch("/api/users/" + clientId, "{\"status\":\"ACTIVE\"}", CLIENT).andExpect(status().isForbidden());
        patch("/api/users/" + clientId, "{\"password\":\"An0ther!Value-2\"}", CLIENT).andExpect(status().isForbidden());
        patch("/api/users/" + clientId, "{\"role\":\"ADMIN\"}", OPERATOR).andExpect(status().isForbidden());
        assertThat(service.getUser(clientId).role()).isEqualTo(Role.CLIENT);
    }

    @Test
    void roleChangeAppliesToTheNextRequest() throws Exception {
        mvc.perform(as(get("/api/users"), CLIENT)).andExpect(status().isForbidden());
        patch("/api/users/" + clientId, "{\"role\":\"OPERATOR\"}", ADMIN).andExpect(status().isOk());
        mvc.perform(as(get("/api/users"), CLIENT)).andExpect(status().isOk());
    }

    @Test
    void lastActiveAdministratorCannotBeRemoved() throws Exception {
        patch("/api/users/" + adminId, "{\"role\":\"CLIENT\"}", ADMIN).andExpect(status().isConflict());
        patch("/api/users/" + adminId, "{\"status\":\"BLOCKED\"}", ADMIN).andExpect(status().isConflict());
        delete_("/api/users/" + adminId, ADMIN).andExpect(status().isConflict());
        patch("/api/users/" + operatorId, "{\"role\":\"ADMIN\"}", ADMIN).andExpect(status().isOk());
        patch("/api/users/" + adminId, "{\"role\":\"CLIENT\"}", ADMIN).andExpect(status().isOk());
    }

    // --- vehicles ---

    @Test
    void clientSeesOnlyOwnVehiclesAndCannotProbeForeignIds() throws Exception {
        mvc.perform(as(get("/api/vehicles"), CLIENT)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(vehicle));
        mvc.perform(as(get("/api/vehicles"), OPERATOR)).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(as(get("/api/vehicles/" + vehicle), CLIENT)).andExpect(status().isOk());
        mvc.perform(as(get("/api/vehicles/" + otherVehicle), CLIENT)).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/vehicles/999999"), CLIENT)).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/vehicles/999999"), OPERATOR)).andExpect(status().isNotFound());
    }

    @Test
    void clientCreatesAndTransfersVehiclesOnlyForThemselves() throws Exception {
        post("/api/vehicles", "{\"plateNumber\":\"C333CC77\",\"ownerId\":%d}".formatted(clientId), CLIENT).andExpect(status().isCreated());
        post("/api/vehicles", "{\"plateNumber\":\"D444DD77\",\"ownerId\":%d}".formatted(otherId), CLIENT).andExpect(status().isForbidden());
        post("/api/vehicles", "{\"plateNumber\":\"D444DD77\"}", CLIENT).andExpect(status().isBadRequest());
        patch("/api/vehicles/" + vehicle, "{\"ownerId\":%d}".formatted(otherId), CLIENT).andExpect(status().isForbidden());
        patch("/api/vehicles/" + otherVehicle, "{\"plateNumber\":\"E555EE77\"}", CLIENT).andExpect(status().isForbidden());
        patch("/api/vehicles/" + vehicle, "{\"ownerId\":%d}".formatted(otherId), OPERATOR).andExpect(status().isOk());
    }

    @Test
    void clientCannotHideParkedVehicleButStaffCan() throws Exception {
        long id = created(post("/api/operations/enter", session(vehicle), CLIENT).andExpect(status().isOk()));
        delete_("/api/vehicles/" + vehicle, CLIENT).andExpect(status().isConflict());
        patch("/api/vehicles/" + vehicle, "{\"plateNumber\":\"F666FF77\"}", CLIENT).andExpect(status().isConflict());
        post("/api/operations/sessions/%d/exit".formatted(id), null, CLIENT).andExpect(status().isOk());
        patch("/api/vehicles/" + vehicle, "{\"plateNumber\":\"F666FF77\"}", CLIENT).andExpect(status().isOk());
        delete_("/api/vehicles/" + vehicle, CLIENT).andExpect(status().isNoContent());
    }

    // --- zones ---

    @Test
    void zonesAreReadableByAllAndManagedByAdministrator() throws Exception {
        mvc.perform(as(get("/api/parking-zones"), CLIENT)).andExpect(status().isOk());
        String body = "{\"name\":\"Новая\",\"capacity\":1,\"hourlyPrice\":10}";
        post("/api/parking-zones", body, CLIENT).andExpect(status().isForbidden());
        post("/api/parking-zones", body, OPERATOR).andExpect(status().isForbidden());
        patch("/api/parking-zones/" + zone, "{\"hourlyPrice\":1}", OPERATOR).andExpect(status().isForbidden());
        delete_("/api/parking-zones/" + zone, OPERATOR).andExpect(status().isForbidden());
        post("/api/parking-zones", body, ADMIN).andExpect(status().isCreated());
    }

    // --- sessions and operations ---

    @Test
    void parkingOperationsAreLimitedToOwnVehicles() throws Exception {
        post("/api/operations/enter", session(otherVehicle), CLIENT).andExpect(status().isForbidden());
        post("/api/parking-sessions", session(otherVehicle), CLIENT).andExpect(status().isForbidden());
        post("/api/operations/enter", "{}", CLIENT).andExpect(status().isBadRequest());
        long own = created(post("/api/operations/enter", session(vehicle), CLIENT).andExpect(status().isOk()));
        long foreign = created(post("/api/operations/enter", session(otherVehicle), OPERATOR).andExpect(status().isOk()));

        mvc.perform(as(get("/api/parking-sessions"), CLIENT)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(as(get("/api/parking-sessions"), OPERATOR)).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(as(get("/api/parking-sessions/" + foreign), CLIENT)).andExpect(status().isForbidden());

        String move = "{\"zoneId\":%d}".formatted(secondZone);
        post("/api/operations/sessions/%d/exit".formatted(foreign), null, CLIENT).andExpect(status().isForbidden());
        post("/api/operations/sessions/%d/move".formatted(foreign), move, CLIENT).andExpect(status().isForbidden());
        patch("/api/parking-sessions/" + foreign, "{\"close\":true}", CLIENT).andExpect(status().isForbidden());
        post("/api/operations/sessions/%d/move".formatted(own), move, CLIENT).andExpect(status().isOk());
        post("/api/operations/sessions/%d/exit".formatted(foreign), null, OPERATOR).andExpect(status().isOk());
        // Authorization passed, the business rule still applies.
        post("/api/operations/sessions/%d/exit".formatted(foreign), null, OPERATOR).andExpect(status().isConflict());
    }

    @Test
    void sessionRecordsAreCorrectedOnlyByAdministrator() throws Exception {
        long id = created(post("/api/operations/enter", session(vehicle), CLIENT).andExpect(status().isOk()));
        String correction = "{\"zoneId\":%d}".formatted(secondZone);
        patch("/api/parking-sessions/" + id, correction, CLIENT).andExpect(status().isForbidden());
        patch("/api/parking-sessions/" + id, correction, OPERATOR).andExpect(status().isForbidden());
        patch("/api/parking-sessions/" + id, correction, ADMIN).andExpect(status().isOk());
        delete_("/api/parking-sessions/" + id, CLIENT).andExpect(status().isForbidden());
        delete_("/api/parking-sessions/" + id, OPERATOR).andExpect(status().isForbidden());
        patch("/api/parking-sessions/" + id, "{\"close\":true}", CLIENT).andExpect(status().isOk());
        delete_("/api/parking-sessions/" + id, ADMIN).andExpect(status().isNoContent());
    }

    @Test
    void onlyStaffClearsZone() throws Exception {
        String clear = "/api/operations/zones/%d/clear".formatted(zone);
        post(clear, null, CLIENT).andExpect(status().isForbidden());
        post(clear, null, OPERATOR).andExpect(status().isConflict());
        post("/api/operations/enter", session(vehicle), CLIENT).andExpect(status().isOk());
        post(clear, null, OPERATOR).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    // --- passes ---

    @Test
    void clientCannotGrantThemselvesFreeParking() throws Exception {
        String body = pass(vehicle);
        post("/api/parking-passes", body, CLIENT).andExpect(status().isForbidden());
        long id = created(post("/api/parking-passes", body, OPERATOR).andExpect(status().isCreated()));
        patch("/api/parking-passes/" + id, "{\"validUntil\":\"2030-01-01T00:00:00Z\"}", CLIENT).andExpect(status().isForbidden());
        patch("/api/parking-passes/" + id, "{\"status\":\"ACTIVE\"}", CLIENT).andExpect(status().isForbidden());
        patch("/api/parking-passes/" + id, "{\"status\":\"CANCELLED\",\"zoneId\":%d}".formatted(secondZone), CLIENT).andExpect(status().isForbidden());
        delete_("/api/parking-passes/" + id, CLIENT).andExpect(status().isForbidden());
        delete_("/api/parking-passes/" + id, OPERATOR).andExpect(status().isForbidden());
        patch("/api/parking-passes/" + id, "{\"status\":\"CANCELLED\"}", CLIENT).andExpect(status().isOk());
        delete_("/api/parking-passes/" + id, ADMIN).andExpect(status().isNoContent());
    }

    @Test
    void renewalDependsOnOwnerPeriodAndPassState() throws Exception {
        long own = created(post("/api/parking-passes", pass(vehicle), OPERATOR).andExpect(status().isCreated()));
        long foreign = created(post("/api/parking-passes", pass(otherVehicle), OPERATOR).andExpect(status().isCreated()));
        String renew = "/api/operations/passes/%d/renew";
        mvc.perform(as(get("/api/parking-passes"), CLIENT)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(as(get("/api/parking-passes/" + foreign), CLIENT)).andExpect(status().isForbidden());
        post(renew.formatted(foreign), "{\"days\":7}", CLIENT).andExpect(status().isForbidden());
        post(renew.formatted(own), "{\"days\":31}", CLIENT).andExpect(status().isForbidden());
        post(renew.formatted(own), "{\"days\":30}", CLIENT).andExpect(status().isOk());
        post(renew.formatted(own), "{\"days\":31}", OPERATOR).andExpect(status().isOk());
        post(renew.formatted(own), "{\"days\":366}", OPERATOR).andExpect(status().isBadRequest());
        patch("/api/parking-passes/" + own, "{\"status\":\"CANCELLED\"}", CLIENT).andExpect(status().isOk());
        post(renew.formatted(own), "{\"days\":7}", CLIENT).andExpect(status().isConflict());
    }

    // --- failure handling ---

    @Test
    void failureKindsAreDistinguishableAndHideInternals() throws Exception {
        mvc.perform(get("/api/parking-zones")).andExpect(status().isUnauthorized());
        post("/api/parking-zones", "{\"name\":\"X\",\"capacity\":1,\"hourlyPrice\":1}", CLIENT).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Недостаточно прав для этой операции"));
        post("/api/operations/zones/%d/clear".formatted(zone), null, ADMIN).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("В зоне нет открытых сессий"));
        mvc.perform(as(get("/actuator/env"), ADMIN)).andExpect(status().isForbidden());
        mvc.perform(as(get("/"), ADMIN)).andExpect(status().isForbidden());
    }

    private long account(String name, String email, Role role) {
        return service.createUser(new UserRequest(name, email, PASSWORD, role, AccountStatus.ACTIVE)).id();
    }

    private String session(long vehicleId) {
        return "{\"vehicleId\":%d,\"zoneId\":%d}".formatted(vehicleId, zone);
    }

    private String pass(long vehicleId) {
        return "{\"vehicleId\":%d,\"zoneId\":%d,\"validFrom\":\"%s\",\"validUntil\":\"%s\"}"
                .formatted(vehicleId, zone, START, START.plusSeconds(86400));
    }

    private long created(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String email) {
        return request.with(httpBasic(email, PASSWORD));
    }

    private MockHttpServletRequestBuilder rawPost(String url) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url);
    }

    private ResultActions register(String body) throws Exception {
        return mvc.perform(rawPost("/api/auth/register")
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions post(String url, String body, String email) throws Exception {
        var request = as(rawPost(url), email).with(csrf());
        return mvc.perform(body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions patch(String url, String body, String email) throws Exception {
        return mvc.perform(as(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(url), email).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions delete_(String url, String email) throws Exception {
        return mvc.perform(as(delete(url), email).with(csrf()));
    }
}
