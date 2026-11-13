package mtuci.task2.controller;

import java.net.URI;
import java.util.List;
import mtuci.task2.dto.Requests.SessionCreate;
import mtuci.task2.dto.Requests.SessionPatch;
import mtuci.task2.model.ParkingSession;
import mtuci.task2.model.Permission;
import mtuci.task2.security.AccountPrincipal;
import mtuci.task2.service.ParkingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parking-sessions")
public class ParkingSessionController {
    private final ParkingService service;

    public ParkingSessionController(ParkingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ParkingSession> list(@AuthenticationPrincipal AccountPrincipal me) {
        return me.can(Permission.SESSION_ANY) ? service.listSessions() : service.listSessionsOf(me.id());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SESSION_ANY') or @access.ownsSession(#id)")
    public ParkingSession get(@PathVariable Long id) {
        return service.getSession(id);
    }

    @PostMapping
    @PreAuthorize("@access.canUseVehicle(#request.vehicleId())")
    public ResponseEntity<ParkingSession> create(@RequestBody SessionCreate request) {
        ParkingSession created = service.createSession(request);
        return ResponseEntity.created(URI.create("/api/parking-sessions/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@access.canPatchSession(#id, #request)")
    public ParkingSession update(@PathVariable Long id, @RequestBody SessionPatch request) {
        return service.updateSession(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SESSION_CORRECT')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteSession(id);
        return ResponseEntity.noContent().build();
    }
}
