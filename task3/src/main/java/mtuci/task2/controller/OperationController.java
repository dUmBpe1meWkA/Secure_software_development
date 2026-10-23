package mtuci.task2.controller;

import java.util.List;
import mtuci.task2.dto.Requests.*;
import mtuci.task2.model.*;
import mtuci.task2.service.ParkingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/operations")
public class OperationController {
    private final ParkingService service;
    public OperationController(ParkingService service) { this.service = service; }
    public record MoveRequest(Long zoneId) {}
    public record RenewRequest(Integer days) {}

    @PostMapping("/enter")
    public ParkingSession enter(@RequestBody SessionCreate request) { return service.createSession(request); }
    @PostMapping("/sessions/{id}/exit")
    public ParkingSession exit(@PathVariable Long id) {
        return service.updateSession(id, new SessionPatch(null, null, true));
    }
    @PostMapping("/sessions/{id}/move")
    public List<ParkingSession> move(@PathVariable Long id, @RequestBody MoveRequest request) {
        return service.moveSession(id, request.zoneId());
    }
    @PostMapping("/passes/{id}/renew")
    public ParkingPass renew(@PathVariable Long id, @RequestBody RenewRequest request) {
        return service.renewPass(id, request.days());
    }
    @PostMapping("/zones/{id}/clear")
    public List<ParkingSession> clear(@PathVariable Long id) { return service.clearZone(id); }
}
