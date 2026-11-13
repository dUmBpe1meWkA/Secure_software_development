package mtuci.task2.controller;

import java.net.URI;
import java.util.List;
import mtuci.task2.dto.Requests.VehicleRequest;
import mtuci.task2.model.Permission;
import mtuci.task2.model.Vehicle;
import mtuci.task2.security.AccountPrincipal;
import mtuci.task2.service.ParkingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {
    private final ParkingService service;

    public VehicleController(ParkingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<Vehicle> list(@AuthenticationPrincipal AccountPrincipal me) {
        return me.can(Permission.VEHICLE_ANY) ? service.listVehicles() : service.listVehiclesOf(me.id());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VEHICLE_ANY') or @access.ownsVehicle(#id)")
    public Vehicle get(@PathVariable Long id) {
        return service.getVehicle(id);
    }

    @PostMapping
    @PreAuthorize("@access.canAssignOwner(#request.ownerId())")
    public ResponseEntity<Vehicle> create(@RequestBody VehicleRequest request) {
        Vehicle created = service.createVehicle(request);
        return ResponseEntity.created(URI.create("/api/vehicles/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('VEHICLE_ANY') or (@access.ownsVehicle(#id) and @access.canAssignOwner(#request.ownerId()))")
    public Vehicle update(@AuthenticationPrincipal AccountPrincipal me, @PathVariable Long id, @RequestBody VehicleRequest request) {
        return me.can(Permission.VEHICLE_ANY) ? service.updateVehicle(id, request) : service.updateOwnVehicle(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('VEHICLE_ANY') or @access.ownsVehicle(#id)")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AccountPrincipal me, @PathVariable Long id) {
        if (me.can(Permission.VEHICLE_ANY)) {
            service.deleteVehicle(id);
        } else {
            service.deleteOwnVehicle(id);
        }
        return ResponseEntity.noContent().build();
    }
}
