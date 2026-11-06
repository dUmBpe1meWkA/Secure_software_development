package mtuci.task2.model;

import static mtuci.task2.model.Permission.*;

import java.util.Set;

public enum Role {
    /** Works only with their own profile, vehicles, sessions and passes. */
    CLIENT(Set.of()),
    /** Parking attendant: serves any vehicle and issues passes, but manages neither accounts nor tariffs. */
    OPERATOR(Set.of(USER_READ, VEHICLE_ANY, ZONE_CLEAR, SESSION_ANY, PASS_MANAGE)),
    ADMIN(Set.of(Permission.values()));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> permissions() {
        return permissions;
    }
}
