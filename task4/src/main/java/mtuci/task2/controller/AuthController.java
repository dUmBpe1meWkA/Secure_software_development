package mtuci.task2.controller;

import java.net.URI;
import mtuci.task2.dto.Requests.ChangePasswordRequest;
import mtuci.task2.dto.Requests.RegisterRequest;
import mtuci.task2.model.User;
import mtuci.task2.security.AccountPrincipal;
import mtuci.task2.service.ParkingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final ParkingService service;

    public AuthController(ParkingService service) {
        this.service = service;
    }

    public record CsrfResponse(String headerName, String token) {}

    @GetMapping("/csrf")
    @PreAuthorize("permitAll()")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/register")
    @PreAuthorize("permitAll()")
    public ResponseEntity<User> register(@RequestBody RegisterRequest request) {
        User created = service.register(request);
        return ResponseEntity.created(URI.create("/api/users/" + created.id())).body(created);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public User me(@AuthenticationPrincipal AccountPrincipal me) {
        return service.getUser(me.id());
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AccountPrincipal me,
            @RequestBody ChangePasswordRequest request) {
        service.changePassword(me.id(), request);
        return ResponseEntity.noContent().build();
    }
}
