package mtuci.task2.controller;

import mtuci.task2.dto.Pair;
import mtuci.task2.dto.Requests.RegisterRequest;
import mtuci.task2.exception.ApiException;
import mtuci.task2.security.JwtService;
import mtuci.task2.service.AuthService;
import mtuci.task2.service.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService auth;
    private final SessionService sessions;

    public AuthController(AuthService auth, SessionService sessions) {
        this.auth = auth;
        this.sessions = sessions;
    }

    public record LoginRequest(String email, String password, String deviceId) {}
    public record RefreshRequest(String refreshToken) {}

    @PostMapping("/login")
    public Pair login(@RequestBody LoginRequest request) {
        required(request.email(), "email");
        required(request.password(), "password");
        return auth.login(request.email(), request.password(), request.deviceId());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Pair register(@RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/refresh")
    public Pair refresh(@RequestBody RefreshRequest request) {
        required(request.refreshToken(), "refreshToken");
        return sessions.refresh(request.refreshToken()).orElseThrow(JwtService::rejected);
    }

    private void required(String value, String field) {
        if (value == null || value.isBlank()) throw ApiException.badRequest("Обязательное поле: " + field);
    }
}
