package mtuci.task2.controller;

import java.net.URI;
import java.util.List;
import mtuci.task2.dto.Requests.ZoneRequest;
import mtuci.task2.model.ParkingZone;
import mtuci.task2.service.ParkingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parking-zones")
public class ParkingZoneController {
    private final ParkingService service;

    public ParkingZoneController(ParkingService service) {
        this.service = service;
    }

    @GetMapping
    public List<ParkingZone> list() {
        return service.listZones();
    }

    @GetMapping("/{id}")
    public ParkingZone get(@PathVariable Long id) {
        return service.getZone(id);
    }

    @PostMapping
    public ResponseEntity<ParkingZone> create(@RequestBody ZoneRequest request) {
        ParkingZone created = service.createZone(request);
        return ResponseEntity.created(URI.create("/api/parking-zones/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    public ParkingZone update(@PathVariable Long id, @RequestBody ZoneRequest request) {
        return service.updateZone(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteZone(id);
        return ResponseEntity.noContent().build();
    }
}
