package mtuci.task2.security;

import java.time.Clock;
import java.util.Locale;
import mtuci.task2.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountDetailsService implements UserDetailsService {
    private final UserRepository users;
    private final Clock clock;

    public AccountDetailsService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        String email = username.strip().toLowerCase(Locale.ROOT);
        return users.findByEmail(email)
                .map(user -> new AccountPrincipal(user, users.isLocked(email, clock.instant())))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
    }
}
