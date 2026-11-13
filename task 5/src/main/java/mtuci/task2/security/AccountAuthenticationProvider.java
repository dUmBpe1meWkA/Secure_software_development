package mtuci.task2.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import mtuci.task2.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Counts failed logins and locks the account temporarily. The account state is checked only after
 * the password, so a stranger cannot learn from the response that an account is blocked.
 */
public class AccountAuthenticationProvider extends DaoAuthenticationProvider {
    public static final int MAX_FAILED_LOGINS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final Logger log = LoggerFactory.getLogger(AccountAuthenticationProvider.class);

    private final UserRepository users;
    private final Clock clock;

    public AccountAuthenticationProvider(UserDetailsService details, PasswordEncoder encoder,
            UserRepository users, Clock clock) {
        super(details);
        setPasswordEncoder(encoder);
        setPreAuthenticationChecks(account -> {});
        setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
        this.users = users;
        this.clock = clock;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = String.valueOf(authentication.getName()).strip().toLowerCase(Locale.ROOT);
        try {
            Authentication result = super.authenticate(authentication);
            users.clearFailedLogins(email);
            return result;
        } catch (BadCredentialsException exception) {
            Instant now = clock.instant();
            users.recordFailedLogin(email, MAX_FAILED_LOGINS, now, now.plus(LOCK_DURATION));
            throw exception;
        } catch (AuthenticationException exception) {
            log.warn("Login rejected by account state: {}", email.replaceAll("[\\r\\n]", "_"));
            throw exception;
        }
    }
}
