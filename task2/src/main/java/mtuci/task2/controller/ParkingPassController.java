package mtuci.task2.controller;

import java.net.URI;
import java.util.List;
import mtuci.task2.dto.Requests.PassRequest;
import mtuci.task2.model.ParkingPass;
import mtuci.task2.service.ParkingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parking-passes")
public class ParkingPassController {
    private final ParkingService service;

    public ParkingPassController(ParkingService service) {
        this.service = service;
    }

    @GetMapping
    public List<ParkingPass> list() {
        return service.listPasses();
    }

    @GetMapping("/{id}")
    public ParkingPass get(@PathVariable Long id) {
        return service.getPass(id);
    }

    @PostMapping
    public ResponseEntity<ParkingPass> create(@RequestBody PassRequest request) {
        ParkingPass created = service.createPass(request);
        return ResponseEntity.created(URI.create("/api/parking-passes/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    public ParkingPass update(@PathVariable Long id, @RequestBody PassRequest request) {
        return service.updatePass(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deletePass(id);
        return ResponseEntity.noContent().build();
    }
}
