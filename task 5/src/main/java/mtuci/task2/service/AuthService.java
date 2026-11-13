package mtuci.task2.service;

import mtuci.task2.dto.Pair;
import mtuci.task2.dto.Requests.RegisterRequest;
import mtuci.task2.security.AccountPrincipal;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AuthenticationProvider authentication;
    private final ParkingService parking;
    private final SessionService sessions;

    public AuthService(AuthenticationProvider authentication, ParkingService parking, SessionService sessions) {
        this.authentication = authentication;
        this.parking = parking;
        this.sessions = sessions;
    }

    public Pair login(String email, String password, String deviceId) {
        var result = authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        var principal = (AccountPrincipal) result.getPrincipal();
        return sessions.issue(parking.getUser(principal.id()), deviceId);
    }

    @Transactional
    public Pair register(RegisterRequest request) {
        return sessions.issue(parking.register(request), request.deviceId());
    }
}
