package mtuci.task2.model;

/** Fine-grained rights; roles below are only named sets of them. */
public enum Permission {
    USER_READ, USER_MANAGE,
    VEHICLE_ANY,
    ZONE_MANAGE, ZONE_CLEAR,
    SESSION_ANY, SESSION_CORRECT,
    PASS_MANAGE, PASS_DELETE
}
