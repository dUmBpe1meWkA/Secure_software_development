package mtuci.task2.config;

import static org.springframework.http.HttpMethod.*;

import jakarta.servlet.DispatcherType;
import java.time.Clock;
import mtuci.task2.model.Permission;
import mtuci.task2.repository.UserRepository;
import mtuci.task2.security.AccountAuthenticationProvider;
import mtuci.task2.security.SecurityErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/** Coarse URL rules live here; ownership and operation context are checked by @PreAuthorize on controllers. */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(UserDetailsService details, PasswordEncoder encoder,
            UserRepository users, Clock clock) {
        return new AccountAuthenticationProvider(details, encoder, users, clock);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorHandler errors) throws Exception {
        // Browsers resend Basic credentials on their own, so stateless requests still need CSRF protection.
        var csrfTokens = new CookieCsrfTokenRepository();
        csrfTokens.setCookieCustomizer(cookie -> cookie.sameSite("Strict"));
        http
                // Without a session every Basic request looks like a new login, and the default strategy
                // would delete the token each time. The token is bound to its cookie, not to a user.
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens)
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
                .httpBasic(basic -> basic.authenticationEntryPoint(errors))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(POST, "/api/auth/register").permitAll()
                        .requestMatchers(GET, "/api/users").hasAuthority(Permission.USER_READ.name())
                        .requestMatchers(POST, "/api/users").hasAuthority(Permission.USER_MANAGE.name())
                        .requestMatchers(DELETE, "/api/users/*").hasAuthority(Permission.USER_MANAGE.name())
                        .requestMatchers(POST, "/api/parking-zones").hasAuthority(Permission.ZONE_MANAGE.name())
                        .requestMatchers(PATCH, "/api/parking-zones/*").hasAuthority(Permission.ZONE_MANAGE.name())
                        .requestMatchers(DELETE, "/api/parking-zones/*").hasAuthority(Permission.ZONE_MANAGE.name())
                        .requestMatchers(DELETE, "/api/parking-sessions/*").hasAuthority(Permission.SESSION_CORRECT.name())
                        .requestMatchers(POST, "/api/parking-passes").hasAuthority(Permission.PASS_MANAGE.name())
                        .requestMatchers(DELETE, "/api/parking-passes/*").hasAuthority(Permission.PASS_DELETE.name())
                        .requestMatchers(POST, "/api/operations/zones/*/clear").hasAuthority(Permission.ZONE_CLEAR.name())
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll());
        return http.build();
    }
}
