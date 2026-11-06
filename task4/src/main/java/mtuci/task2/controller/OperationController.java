package mtuci.task2.controller;

import java.util.List;
import mtuci.task2.dto.Requests.*;
import mtuci.task2.model.*;
import mtuci.task2.service.ParkingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/operations")
public class OperationController {
    private final ParkingService service;
    public OperationController(ParkingService service) { this.service = service; }
    public record MoveRequest(Long zoneId) {}
    public record RenewRequest(Integer days) {}

    @PostMapping("/enter")
    @PreAuthorize("@access.canUseVehicle(#request.vehicleId())")
    public ParkingSession enter(@RequestBody SessionCreate request) { return service.createSession(request); }
    @PostMapping("/sessions/{id}/exit")
    @PreAuthorize("hasAuthority('SESSION_ANY') or @access.ownsSession(#id)")
    public ParkingSession exit(@PathVariable Long id) {
        return service.updateSession(id, new SessionPatch(null, null, true));
    }
    @PostMapping("/sessions/{id}/move")
    @PreAuthorize("hasAuthority('SESSION_ANY') or @access.ownsSession(#id)")
    public List<ParkingSession> move(@PathVariable Long id, @RequestBody MoveRequest request) {
        return service.moveSession(id, request.zoneId());
    }
    @PostMapping("/passes/{id}/renew")
    @PreAuthorize("@access.canRenewPass(#id, #request.days())")
    public ParkingPass renew(@PathVariable Long id, @RequestBody RenewRequest request) {
        return service.renewPass(id, request.days());
    }
    @PostMapping("/zones/{id}/clear")
    @PreAuthorize("hasAuthority('ZONE_CLEAR')")
    public List<ParkingSession> clear(@PathVariable Long id) { return service.clearZone(id); }
}
