package mtuci.task2.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import mtuci.task2.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final Clock clock;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(@Value("${jwt.secret}") String secret, @Value("${jwt.access-ttl}") Duration accessTtl,
            @Value("${jwt.refresh-ttl}") Duration refreshTtl, Clock clock) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        if (accessTtl.toSeconds() < 1 || refreshTtl.toSeconds() < 1) {
            throw new IllegalArgumentException("JWT TTL must be at least one second");
        }
        this.clock = clock;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    public record Issued(String value, Instant expiry) {}

    public Issued createAccess(String username) { return create(username, "access", accessTtl); }
    public Issued createRefresh(String username) { return create(username, "refresh", refreshTtl); }
    public Claims validateAccess(String value) { return validate(value, "access"); }
    public Claims validateRefresh(String value) { return validate(value, "refresh"); }

    private Issued create(String username, String type, Duration ttl) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiry = now.plusSeconds(ttl.toSeconds());
        String value = Jwts.builder().subject(username).id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now)).expiration(Date.from(expiry)).claim("type", type)
                .signWith(key, Jwts.SIG.HS256).compact();
        return new Issued(value, expiry);
    }

    private Claims validate(String value, String type) {
        try {
            var jwt = Jwts.parser().verifyWith(key).clock(() -> Date.from(clock.instant()))
                    .require("type", type).build().parseSignedClaims(value);
            Claims claims = jwt.getPayload();
            if (!"HS256".equals(jwt.getHeader().getAlgorithm())
                    || claims.getSubject() == null || claims.getSubject().isBlank()
                    || claims.getId() == null || claims.getId().isBlank()
                    || claims.getExpiration() == null
                    || !claims.getExpiration().toInstant().isAfter(clock.instant())) {
                throw rejected();
            }
            return claims;
        } catch (JwtException | IllegalArgumentException exception) {
            throw rejected();
        }
    }

    public static ApiException rejected() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Недействительный токен или сессия");
    }
}
