package mtuci.task2.service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import mtuci.task2.dto.Pair;
import mtuci.task2.model.AccountStatus;
import mtuci.task2.model.SessionStatus;
import mtuci.task2.model.User;
import mtuci.task2.model.UserSession;
import mtuci.task2.repository.UserRepository;
import mtuci.task2.repository.UserSessionRepository;
import mtuci.task2.security.JwtService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SessionService {
    private final UserSessionRepository sessions;
    private final UserRepository users;
    private final JwtService jwt;
    private final Clock clock;

    public SessionService(UserSessionRepository sessions, UserRepository users, JwtService jwt, Clock clock) {
        this.sessions = sessions;
        this.users = users;
        this.jwt = jwt;
        this.clock = clock;
    }

    public Pair issue(User user, String deviceId) {
        var access = jwt.createAccess(user.email());
        var refresh = jwt.createRefresh(user.email());
        sessions.save(new UserSession(UUID.randomUUID(), user.email(), deviceId, access.value(), refresh.value(),
                access.expiry(), refresh.expiry(), SessionStatus.ACTIVE));
        return new Pair(access.value(), refresh.value());
    }

    public Optional<Pair> refresh(String refresh) {
        String subject = jwt.validateRefresh(refresh).getSubject();
        UserSession old = sessions.findForUpdate(refresh).orElseThrow(JwtService::rejected);
        User user = users.findByEmail(old.username()).orElseThrow(JwtService::rejected);
        if (!subject.equals(old.username()) || user.status() != AccountStatus.ACTIVE
                || users.isLocked(user.email(), clock.instant())
                || !old.refreshTokenExpiry().isAfter(clock.instant())) {
            throw JwtService.rejected();
        }
        if (old.status() == SessionStatus.USED) {
            sessions.setStatus(old.id(), SessionStatus.REVOKED);
            // The controller sends 401 after this transaction commits the revocation.
            return Optional.empty();
        }
        if (old.status() != SessionStatus.ACTIVE) throw JwtService.rejected();
        sessions.setStatus(old.id(), SessionStatus.USED);
        return Optional.of(issue(user, old.deviceId()));
    }
}
