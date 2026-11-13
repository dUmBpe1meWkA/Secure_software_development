package mtuci.task2.controller;

import java.net.URI;
import java.util.List;
import mtuci.task2.dto.Requests.PassRequest;
import mtuci.task2.model.ParkingPass;
import mtuci.task2.model.Permission;
import mtuci.task2.security.AccountPrincipal;
import mtuci.task2.service.ParkingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parking-passes")
public class ParkingPassController {
    private final ParkingService service;

    public ParkingPassController(ParkingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ParkingPass> list(@AuthenticationPrincipal AccountPrincipal me) {
        return me.can(Permission.PASS_MANAGE) ? service.listPasses() : service.listPassesOf(me.id());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PASS_MANAGE') or @access.ownsPass(#id)")
    public ParkingPass get(@PathVariable Long id) {
        return service.getPass(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PASS_MANAGE')")
    public ResponseEntity<ParkingPass> create(@RequestBody PassRequest request) {
        ParkingPass created = service.createPass(request);
        return ResponseEntity.created(URI.create("/api/parking-passes/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@access.canPatchPass(#id, #request)")
    public ParkingPass update(@PathVariable Long id, @RequestBody PassRequest request) {
        return service.updatePass(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PASS_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deletePass(id);
        return ResponseEntity.noContent().build();
    }
}
