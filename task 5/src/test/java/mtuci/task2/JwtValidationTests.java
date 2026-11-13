package mtuci.task2;

import static org.assertj.core.api.Assertions.*;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import mtuci.task2.exception.ApiException;
import mtuci.task2.dto.Requests.RegisterRequest;
import mtuci.task2.security.JwtService;
import org.junit.jupiter.api.Test;

class JwtValidationTests {
    private static final Instant NOW = Instant.parse("2026-11-13T08:00:00.123Z");
    private static final String SECRET = "unit-test-signing-fixture-at-least-32-bytes";
    private final JwtService jwt = new JwtService(SECRET, Duration.ofMinutes(15), Duration.ofDays(7),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void registrationAcceptsOptionalDeviceAndRejectsElevatedRole() {
        var mapper = tools.jackson.databind.json.JsonMapper.builder()
                .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        var request = mapper.readValue("{\"name\":\"Client\",\"email\":\"client@example.com\","
                + "\"password\":\"test value\",\"deviceId\":\"phone\"}", RegisterRequest.class);
        assertThat(request.deviceId()).isEqualTo("phone");
        assertThatThrownBy(() -> mapper.readValue("{\"role\":\"ADMIN\"}", RegisterRequest.class))
                .isInstanceOf(tools.jackson.databind.exc.UnrecognizedPropertyException.class);
    }

    @Test
    void pairHasDistinctIdsTypesAndSecondPrecisionExpiry() {
        var access = jwt.createAccess("client@example.com");
        var refresh = jwt.createRefresh("client@example.com");
        var a = jwt.validateAccess(access.value());
        var r = jwt.validateRefresh(refresh.value());
        assertThat(a.getId()).isNotEqualTo(r.getId());
        assertThat(a.getExpiration().toInstant()).isEqualTo(access.expiry());
        assertThat(r.getExpiration().toInstant()).isEqualTo(refresh.expiry());
        assertThat(access.expiry().getNano()).isZero();
        assertThat(refresh.expiry().getNano()).isZero();
        assertThatThrownBy(() -> jwt.validateRefresh(access.value())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> jwt.validateAccess(refresh.value())).isInstanceOf(ApiException.class);
    }

    @Test
    void requiresClaimsAndAcceptsJwtWithoutOptionalIssuedAt() {
        var valid = Map.<String, Object>of("sub", "client@example.com", "jti", "unique-id",
                "exp", NOW.plusSeconds(60).getEpochSecond(), "type", "access");
        assertThat(jwt.validateAccess(signed(valid)).getSubject()).isEqualTo("client@example.com");
        for (String field : valid.keySet()) {
            var missing = new java.util.HashMap<>(valid);
            missing.remove(field);
            assertThatThrownBy(() -> jwt.validateAccess(signed(missing))).isInstanceOf(ApiException.class);
        }
    }

    @Test
    void rejectsExpirationBoundaryTamperedSignatureAndOtherAlgorithm() {
        var access = jwt.createAccess("client@example.com");
        var later = new JwtService(SECRET, Duration.ofMinutes(15), Duration.ofDays(7),
                Clock.fixed(access.expiry(), ZoneOffset.UTC));
        assertThatThrownBy(() -> later.validateAccess(access.value())).isInstanceOf(ApiException.class);
        String[] parts = access.value().split("\\.");
        String forged = parts[0] + "." + parts[1] + "." + "A".repeat(43);
        assertThatThrownBy(() -> jwt.validateAccess(forged)).isInstanceOf(ApiException.class);
        String longSecret = "x".repeat(64);
        var longKey = Keys.hmacShaKeyFor(longSecret.getBytes(StandardCharsets.UTF_8));
        String hs384 = Jwts.builder().subject("client@example.com").id("id").claim("type", "access")
                .expiration(Date.from(NOW.plusSeconds(60))).signWith(longKey, Jwts.SIG.HS384).compact();
        var sameKey = new JwtService(longSecret, Duration.ofMinutes(15), Duration.ofDays(7), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> sameKey.validateAccess(hs384)).isInstanceOf(ApiException.class);
    }

    private String signed(Map<String, Object> claims) {
        return Jwts.builder().claims(claims)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
    }
}
