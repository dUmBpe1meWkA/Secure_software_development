package mtuci.task2.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final AccountDetailsService accounts;
    private final SecurityErrorHandler errors;

    public JwtFilter(JwtService jwt, AccountDetailsService accounts, SecurityErrorHandler errors) {
        this.jwt = jwt;
        this.accounts = accounts;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().startsWith("/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            try {
                if (!header.startsWith("Bearer ")) throw new BadCredentialsException("Expected Bearer");
                var claims = jwt.validateAccess(header.substring(7));
                var principal = accounts.loadUserByUsername(claims.getSubject());
                if (!principal.isEnabled() || !principal.isAccountNonLocked()) {
                    throw new BadCredentialsException("Account unavailable");
                }
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
            } catch (mtuci.task2.exception.ApiException | AuthenticationException exception) {
                SecurityContextHolder.clearContext();
                errors.commence(request, response, new BadCredentialsException("Invalid access"));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
