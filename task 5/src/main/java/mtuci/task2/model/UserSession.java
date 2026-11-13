package mtuci.task2.model;

import java.time.Instant;
import java.util.UUID;

public record UserSession(UUID id, String username, String deviceId, String accessToken,
        String refreshToken, Instant accessTokenExpiry, Instant refreshTokenExpiry, SessionStatus status) {}
