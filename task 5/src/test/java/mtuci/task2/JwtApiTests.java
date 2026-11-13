package mtuci.task2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import mtuci.task2.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"jwt.access-ttl=PT1M", "jwt.refresh-ttl=PT1H"})
@Import(JwtApiTests.TimeConfiguration.class)
class JwtApiTests extends PostgresTestSupport {
    static final String EMAIL = "client@example.com";
    static final String PASS = "Str0ng!Value-1";
    static final Instant START = Instant.parse("2026-11-13T08:00:00Z");

    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired JwtService jwt;
    MockMvc mvc;

    @BeforeEach
    void prepare() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanUp();
        clock.now = START;
    }

    @AfterEach
    void cleanUp() throws Exception {
        adminSql("ALTER TABLE public.user_sessions DROP CONSTRAINT IF EXISTS test_reject_session");
        adminSql("DELETE FROM public.user_sessions");
        jdbc.update("DELETE FROM app_users");
    }

    @Test
    void registrationPersistsSessionAndIssuesSignedTypedPair() throws Exception {
        Pair pair = register("browser");
        assertThat(jdbc.queryForObject("SELECT role FROM app_users WHERE email=?", String.class, EMAIL)).isEqualTo("CLIENT");
        assertThat(jdbc.queryForObject("SELECT password_hash FROM app_users WHERE email=?", String.class, EMAIL))
                .startsWith("$2").isNotEqualTo(PASS);
        assertThat(jdbc.queryForObject("SELECT username FROM public.user_sessions WHERE refresh_token=?", String.class, pair.refresh))
                .isEqualTo(EMAIL);
        assertThat(jdbc.queryForObject("SELECT device_id FROM public.user_sessions WHERE refresh_token=?", String.class, pair.refresh))
                .isEqualTo("browser");
        assertThat(jdbc.queryForObject("SELECT access_token FROM public.user_sessions WHERE refresh_token=?", String.class, pair.refresh))
                .isEqualTo(pair.access);
        assertThat(jdbc.queryForObject("SELECT id FROM public.user_sessions WHERE refresh_token=?", UUID.class, pair.refresh)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT access_token_expiry FROM public.user_sessions WHERE refresh_token=?", java.sql.Timestamp.class, pair.refresh).toInstant())
                .isEqualTo(START.plusSeconds(60));
        assertThat(jdbc.queryForObject("SELECT refresh_token_expiry FROM public.user_sessions WHERE refresh_token=?", java.sql.Timestamp.class, pair.refresh).toInstant())
                .isEqualTo(START.plusSeconds(3600));
        assertThat(state(pair.refresh)).isEqualTo("ACTIVE");
        assertClaims(pair.access, "access", 60);
        assertClaims(pair.refresh, "refresh", 3600);
        assertThat(claims(pair.access).get("jti").asText()).isNotEqualTo(claims(pair.refresh).get("jti").asText());
        mvc.perform(get("/api/vehicles").header("Authorization", "Bearer " + pair.access)).andExpect(status().isOk());
        mvc.perform(get("/api/vehicles").header("Authorization", "Bearer " + pair.refresh)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginCreatesIndependentSessionsWithUniquePairs() throws Exception {
        Pair registration = register(null);
        Pair first = login("phone");
        Pair second = login("phone");
        assertThat(List.of(registration.access, registration.refresh, first.access, first.refresh, second.access, second.refresh))
                .doesNotHaveDuplicates();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM public.user_sessions WHERE username=? AND status='ACTIVE'", Integer.class, EMAIL))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT device_id FROM public.user_sessions WHERE refresh_token=?", String.class, registration.refresh))
                .isNull();
    }

    @Test
    void invalidRequiredFieldsAre400AndDoNotCreateAccountsOrSessions() throws Exception {
        for (String body : List.of("{}", "{\"email\":\"\",\"password\":\"\"}",
                "{\"email\":\"client@example.com\",\"password\":\"\"}")) {
            send("/auth/login", body).andExpect(status().isBadRequest());
        }
        for (String body : List.of("{}", "{\"refreshToken\":\"\"}", "{\"refreshToken\":\"   \"}")) {
            send("/auth/refresh", body).andExpect(status().isBadRequest());
        }
        for (String field : List.of("name", "email", "password")) {
            var body = new java.util.HashMap<>(Map.of("name", "Client", "email", EMAIL, "password", PASS));
            body.put(field, "");
            send("/auth/register", mapper.writeValueAsString(body)).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class)).isZero();
        assertThat(sessionCount()).isZero();
    }

    @Test
    void duplicateAndElevatedRegistrationCannotCreateAnotherAccount() throws Exception {
        register(null);
        send("/auth/register", mapper.writeValueAsString(Map.of("name", "Other", "email", "CLIENT@example.com", "password", PASS)))
                .andExpect(status().isConflict());
        send("/auth/register", mapper.writeValueAsString(Map.of("name", "Other", "email", "other@example.com", "password", PASS, "role", "ADMIN")))
                .andExpect(status().isBadRequest());
        assertThat(sessionCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class)).isEqualTo(1);
    }

    @Test
    void wrongCredentialsAndUnknownAccountAre401WithoutNewSession() throws Exception {
        register(null);
        send("/auth/login", mapper.writeValueAsString(Map.of("email", EMAIL, "password", "Wrong!Value-1")))
                .andExpect(status().isUnauthorized());
        send("/auth/login", mapper.writeValueAsString(Map.of("email", "unknown@example.com", "password", PASS)))
                .andExpect(status().isUnauthorized());
        assertThat(sessionCount()).isEqualTo(1);
    }

    @Test
    void rotationMarksOldUsedAndReplayCommitsRevocation() throws Exception {
        Pair first = register("phone");
        Pair second = pair(refresh(first.refresh).andExpect(status().isOk()));
        assertThat(second.access).isNotEqualTo(first.access);
        assertThat(second.refresh).isNotEqualTo(first.refresh);
        assertThat(state(first.refresh)).isEqualTo("USED");
        assertThat(state(second.refresh)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT device_id FROM public.user_sessions WHERE refresh_token=?", String.class, second.refresh))
                .isEqualTo("phone");
        refresh(first.refresh).andExpect(status().isUnauthorized());
        assertThat(state(first.refresh)).isEqualTo("REVOKED");
        assertThat(state(second.refresh)).isEqualTo("ACTIVE");
        assertThat(sessionCount()).isEqualTo(2);
        refresh(first.refresh).andExpect(status().isUnauthorized());
        refresh(second.refresh).andExpect(status().isOk());
    }

    @Test
    void accessCannotRefreshAndUnknownValidRefreshCannotCreateSession() throws Exception {
        Pair pair = register(null);
        refresh(pair.access).andExpect(status().isUnauthorized());
        refresh(jwt.createRefresh(EMAIL).value()).andExpect(status().isUnauthorized());
        assertThat(state(pair.refresh)).isEqualTo("ACTIVE");
        assertThat(sessionCount()).isEqualTo(1);
    }

    @Test
    void expiredAccessAndRefreshAreUnauthorized() throws Exception {
        Pair pair = register(null);
        clock.now = START.plusSeconds(61);
        mvc.perform(get("/api/vehicles").header("Authorization", "Bearer " + pair.access)).andExpect(status().isUnauthorized());
        clock.now = START.plusSeconds(3601);
        refresh(pair.refresh).andExpect(status().isUnauthorized());
        assertThat(sessionCount()).isEqualTo(1);
    }

    @Test
    void malformedAndForgedJwtAre401() throws Exception {
        Pair pair = register(null);
        String[] pieces = pair.refresh.split("\\.");
        String forged = pieces[0] + "." + pieces[1] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        for (String value : List.of("invalid", "a.b.c", forged)) {
            refresh(value).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/vehicles").header("Authorization", "Bearer " + value)).andExpect(status().isUnauthorized());
        }
        assertThat(state(pair.refresh)).isEqualTo("ACTIVE");
    }

    @Test
    void signedJwtStillRequiresEveryMandatoryClaimAndCorrectType() throws Exception {
        register(null);
        Map<String, Object> valid = Map.of("sub", EMAIL, "jti", UUID.randomUUID().toString(),
                "iat", START.getEpochSecond(), "exp", START.plusSeconds(60).getEpochSecond(), "type", "access");
        for (String field : List.of("sub", "jti", "exp", "type")) {
            var incomplete = new java.util.HashMap<>(valid);
            incomplete.remove(field);
            mvc.perform(get("/api/vehicles").header("Authorization", "Bearer " + signedJwt(incomplete)))
                    .andExpect(status().isUnauthorized());
        }
        var wrongType = new java.util.HashMap<>(valid);
        wrongType.put("type", "refresh");
        mvc.perform(get("/api/vehicles").header("Authorization", "Bearer " + signedJwt(wrongType)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/vehicles").header("Authorization", "Bearer " + signedJwt(valid)))
                .andExpect(status().isOk());
    }

    @Test
    void revokedSessionCannotRefresh() throws Exception {
        Pair pair = register(null);
        jdbc.update("UPDATE public.user_sessions SET status='REVOKED' WHERE refresh_token=?", pair.refresh);
        refresh(pair.refresh).andExpect(status().isUnauthorized());
        assertThat(state(pair.refresh)).isEqualTo("REVOKED");
        assertThat(sessionCount()).isEqualTo(1);
    }

    @Test
    void simultaneousExchangeIssuesExactlyOneNewPair() throws Exception {
        Pair pair = register("phone");
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Integer> exchange = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return refresh(pair.refresh).andReturn().getResponse().getStatus();
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(exchange);
            var second = executor.submit(exchange);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 401);
        }
        assertThat(sessionCount()).isEqualTo(2);
        assertThat(state(pair.refresh)).isEqualTo("REVOKED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM public.user_sessions WHERE status='ACTIVE'", Integer.class)).isEqualTo(1);
    }

    @Test
    void failedNewSessionInsertRollsBackRotation() throws Exception {
        Pair pair = register("reject-session");
        rejectNewSessions();
        refresh(pair.refresh).andExpect(status().isConflict()).andExpect(jsonPath("$.accessToken").doesNotExist());
        assertThat(state(pair.refresh)).isEqualTo("ACTIVE");
        assertThat(sessionCount()).isEqualTo(1);
        adminSql("ALTER TABLE public.user_sessions DROP CONSTRAINT test_reject_session");
        refresh(pair.refresh).andExpect(status().isOk());
    }

    @Test
    void failedSessionInsertRollsBackRegistration() throws Exception {
        rejectNewSessions();
        send("/auth/register", mapper.writeValueAsString(Map.of("name", "Client", "email", EMAIL, "password", PASS, "deviceId", "reject-session")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.accessToken").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users WHERE email=?", Integer.class, EMAIL)).isZero();
        assertThat(sessionCount()).isZero();
    }

    private Pair register(String device) throws Exception {
        var body = new java.util.HashMap<>(Map.of("name", "Client", "email", EMAIL, "password", PASS));
        if (device != null) body.put("deviceId", device);
        return pair(send("/auth/register", mapper.writeValueAsString(body)).andExpect(status().isCreated()));
    }

    private Pair login(String device) throws Exception {
        return pair(send("/auth/login", mapper.writeValueAsString(Map.of("email", EMAIL, "password", PASS, "deviceId", device)))
                .andExpect(status().isOk()));
    }

    private ResultActions refresh(String value) throws Exception {
        return send("/auth/refresh", mapper.writeValueAsString(Map.of("refreshToken", value)));
    }

    private ResultActions send(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Pair pair(ResultActions result) throws Exception {
        result.andExpect(jsonPath("$.accessToken").isNotEmpty()).andExpect(jsonPath("$.refreshToken").isNotEmpty());
        JsonNode json = mapper.readTree(result.andReturn().getResponse().getContentAsString());
        return new Pair(json.get("accessToken").asText(), json.get("refreshToken").asText());
    }

    private JsonNode claims(String value) throws Exception {
        return mapper.readTree(Base64.getUrlDecoder().decode(value.split("\\.")[1]));
    }

    private void assertClaims(String value, String type, long ttl) throws Exception {
        JsonNode body = claims(value);
        assertThat(body.get("sub").asText()).isEqualTo(EMAIL);
        assertThat(body.get("type").asText()).isEqualTo(type);
        assertThat(body.get("exp").asLong()).isEqualTo(START.plusSeconds(ttl).getEpochSecond());
        assertThat(body.get("iat").asLong()).isEqualTo(START.getEpochSecond());
        assertThat(UUID.fromString(body.get("jti").asText())).isNotNull();
        String[] pieces = value.split("\\.");
        assertThat(pieces[2]).isEqualTo(signature(pieces[0] + "." + pieces[1]));
    }

    private String signedJwt(Map<String, Object> claims) throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String body = encoder.encodeToString(mapper.writeValueAsBytes(claims));
        return header + "." + body + "." + signature(header + "." + body);
    }

    private String signature(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.US_ASCII)));
    }

    private String state(String value) {
        return jdbc.queryForObject("SELECT status FROM public.user_sessions WHERE refresh_token=?", String.class, value);
    }

    private int sessionCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM public.user_sessions", Integer.class);
    }

    private void rejectNewSessions() throws Exception {
        adminSql("ALTER TABLE public.user_sessions ADD CONSTRAINT test_reject_session "
                + "CHECK (status <> 'ACTIVE' OR device_id IS DISTINCT FROM 'reject-session') NOT VALID");
    }

    private void adminSql(String sql) throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    record Pair(String access, String refresh) {}

    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        volatile Instant now = START;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
