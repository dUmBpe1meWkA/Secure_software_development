package mtuci.task2.security;

import mtuci.task2.dto.Requests.PassRequest;
import mtuci.task2.dto.Requests.SessionPatch;
import mtuci.task2.dto.Requests.UserRequest;
import mtuci.task2.model.ParkingPass;
import mtuci.task2.model.ParkingSession;
import mtuci.task2.model.PassStatus;
import mtuci.task2.model.Permission;
import mtuci.task2.repository.ParkingPassRepository;
import mtuci.task2.repository.ParkingSessionRepository;
import mtuci.task2.repository.VehicleRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Rules that depend on the operation context, used from @PreAuthorize. A foreign record and a
 * missing one give the same answer, so a client cannot probe which ids exist.
 */
@Component("access")
public class AccessPolicy {
    public static final int CLIENT_RENEW_DAYS = 30;
    private final VehicleRepository vehicles;
    private final ParkingSessionRepository sessions;
    private final ParkingPassRepository passes;

    public AccessPolicy(VehicleRepository vehicles, ParkingSessionRepository sessions, ParkingPassRepository passes) {
        this.vehicles = vehicles;
        this.sessions = sessions;
        this.passes = passes;
    }

    public boolean isSelf(Long userId) {
        return me().id().equals(userId);
    }

    public boolean ownsVehicle(Long vehicleId) {
        return vehicleId != null && vehicles.findById(vehicleId)
                .filter(vehicle -> vehicle.ownerId().equals(me().id())).isPresent();
    }

    public boolean ownsSession(Long sessionId) {
        return sessionId != null && sessions.findById(sessionId).map(ParkingSession::vehicleId)
                .filter(this::ownsVehicle).isPresent();
    }

    public boolean ownsPass(Long passId) {
        return passId != null && passes.findById(passId).map(ParkingPass::vehicleId)
                .filter(this::ownsVehicle).isPresent();
    }

    /** An absent id passes here so that the service answers with 400 instead of a misleading 403. */
    public boolean canUseVehicle(Long vehicleId) {
        return vehicleId == null || me().can(Permission.SESSION_ANY) || ownsVehicle(vehicleId);
    }

    /** Clients assign vehicles only to themselves. */
    public boolean canAssignOwner(Long ownerId) {
        return ownerId == null || me().can(Permission.VEHICLE_ANY) || isSelf(ownerId);
    }

    /** Password, role and status change only through an administrator; a password also through change-password. */
    public boolean canPatchUser(Long id, UserRequest request) {
        return me().can(Permission.USER_MANAGE) || (isSelf(id)
                && request.password() == null && request.role() == null && request.status() == null);
    }

    /** Closing is everyday work; rewriting the vehicle or zone of a record is an administrative correction. */
    public boolean canPatchSession(Long id, SessionPatch request) {
        if (me().can(Permission.SESSION_CORRECT)) return true;
        return request.vehicleId() == null && request.zoneId() == null
                && (me().can(Permission.SESSION_ANY) || ownsSession(id));
    }

    /** A pass makes parking free, so its owner may only give it up. */
    public boolean canPatchPass(Long id, PassRequest request) {
        if (me().can(Permission.PASS_MANAGE)) return true;
        return request.status() == PassStatus.CANCELLED && request.vehicleId() == null && request.zoneId() == null
                && request.validFrom() == null && request.validUntil() == null && ownsPass(id);
    }

    /** Self-service renewal is short; longer periods go through an operator. */
    public boolean canRenewPass(Long id, Integer days) {
        if (me().can(Permission.PASS_MANAGE)) return true;
        return ownsPass(id) && (days == null || days <= CLIENT_RENEW_DAYS);
    }

    private AccountPrincipal me() {
        return (AccountPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
