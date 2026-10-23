package mtuci.task2.controller;

import java.net.URI;
import java.util.List;
import mtuci.task2.dto.Requests.SessionCreate;
import mtuci.task2.dto.Requests.SessionPatch;
import mtuci.task2.model.ParkingSession;
import mtuci.task2.service.ParkingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parking-sessions")
public class ParkingSessionController {
    private final ParkingService service;

    public ParkingSessionController(ParkingService service) {
        this.service = service;
    }

    @GetMapping
    public List<ParkingSession> list() {
        return service.listSessions();
    }

    @GetMapping("/{id}")
    public ParkingSession get(@PathVariable Long id) {
        return service.getSession(id);
    }

    @PostMapping
    public ResponseEntity<ParkingSession> create(@RequestBody SessionCreate request) {
        ParkingSession created = service.createSession(request);
        return ResponseEntity.created(URI.create("/api/parking-sessions/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    public ParkingSession update(@PathVariable Long id, @RequestBody SessionPatch request) {
        return service.updateSession(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteSession(id);
        return ResponseEntity.noContent().build();
    }
}
