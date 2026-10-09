package mtuci.task2.controller;

import java.net.URI;
import java.util.List;
import mtuci.task2.dto.Requests.VehicleRequest;
import mtuci.task2.model.Vehicle;
import mtuci.task2.service.ParkingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {
    private final ParkingService service;

    public VehicleController(ParkingService service) {
        this.service = service;
    }

    @GetMapping
    public List<Vehicle> list() {
        return service.listVehicles();
    }

    @GetMapping("/{id}")
    public Vehicle get(@PathVariable Long id) {
        return service.getVehicle(id);
    }

    @PostMapping
    public ResponseEntity<Vehicle> create(@RequestBody VehicleRequest request) {
        Vehicle created = service.createVehicle(request);
        return ResponseEntity.created(URI.create("/api/vehicles/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    public Vehicle update(@PathVariable Long id, @RequestBody VehicleRequest request) {
        return service.updateVehicle(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteVehicle(id);
        return ResponseEntity.noContent().build();
    }
}
