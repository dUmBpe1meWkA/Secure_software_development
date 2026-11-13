package mtuci.task2.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record User(Long id, String name, String email, Role role, AccountStatus status,
                   @JsonIgnore String passwordHash) implements Entity {
    @Override
    public String toString() {
        return "User[id=" + id + ", role=" + role + ", status=" + status + "]";
    }
}
