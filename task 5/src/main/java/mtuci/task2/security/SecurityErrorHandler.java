package mtuci.task2.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Clock;
import mtuci.task2.exception.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Writes 401 and 403 in the common error format and logs them without credentials. */
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private static final Logger log = LoggerFactory.getLogger(SecurityErrorHandler.class);
    private final ObjectMapper mapper;
    private final Clock clock;

    public SecurityErrorHandler(ObjectMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        if (exception instanceof InternalAuthenticationServiceException) {
            log.error("Account lookup failed", exception);
            write(request, response, HttpStatus.SERVICE_UNAVAILABLE, "Аутентификация временно недоступна");
            return;
        }
        String message = switch (exception) {
            case LockedException e -> "Учётная запись временно заблокирована после неудачных попыток входа";
            case DisabledException e -> "Учётная запись заблокирована администратором";
            case BadCredentialsException e -> "Неверный email или пароль";
            default -> "Требуется аутентификация";
        };
        log.warn("401 {} {}: {}", request.getMethod(), safe(request.getRequestURI()), exception.getClass().getSimpleName());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(request, response, HttpStatus.UNAUTHORIZED, message);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        Principal user = request.getUserPrincipal();
        log.warn("403 {} {} user={}: {}", request.getMethod(), safe(request.getRequestURI()),
                user == null ? "anonymous" : safe(user.getName()), exception.getClass().getSimpleName());
        write(request, response, HttpStatus.FORBIDDEN, exception instanceof CsrfException
                ? "CSRF-токен отсутствует или неверен; получите его через GET /api/auth/csrf"
                : "Недостаточно прав для этой операции");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8);
        mapper.writeValue(response.getOutputStream(), new ApiError(clock.instant(), status.value(),
                status.getReasonPhrase(), message, request.getRequestURI()));
    }

    private String safe(String value) {
        return value.replaceAll("[\\r\\n]", "_");
    }
}
