package mtuci.task2.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import mtuci.task2.model.AccountStatus;
import mtuci.task2.model.Permission;
import mtuci.task2.model.Role;
import mtuci.task2.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** The account behind one request. Sessions are stateless, so role and status are always current. */
public final class AccountPrincipal implements UserDetails {
    private final Long id;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private final boolean active;
    private final boolean locked;

    public AccountPrincipal(User user, boolean locked) {
        this.id = user.id();
        this.email = user.email();
        this.passwordHash = user.passwordHash();
        this.role = user.role();
        this.active = user.status() == AccountStatus.ACTIVE;
        this.locked = locked;
    }

    public Long id() { return id; }

    public boolean can(Permission permission) { return role.permissions().contains(permission); }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        role.permissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission.name())));
        return authorities;
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonLocked() { return !locked; }
    @Override public boolean isEnabled() { return active; }

    @Override
    public String toString() {
        return "AccountPrincipal[id=" + id + ", role=" + role + "]";
    }
}
