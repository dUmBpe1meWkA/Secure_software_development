package mtuci.task2.config;

import static org.springframework.http.HttpMethod.*;

import jakarta.servlet.DispatcherType;
import java.time.Clock;
import mtuci.task2.model.Permission;
import mtuci.task2.repository.UserRepository;
import mtuci.task2.security.AccountAuthenticationProvider;
import mtuci.task2.security.SecurityErrorHandler;
import mtuci.task2.security.AccountDetailsService;
import mtuci.task2.security.JwtFilter;
import mtuci.task2.security.JwtService;
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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorHandler errors,
            JwtService jwt, AccountDetailsService accounts) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(new JwtFilter(jwt, accounts, errors), UsernamePasswordAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(POST, "/auth/login", "/auth/register", "/auth/refresh").permitAll()
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
