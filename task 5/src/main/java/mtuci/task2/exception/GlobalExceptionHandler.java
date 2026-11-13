package mtuci.task2.exception;

import java.time.Clock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApi(ApiException exception, WebRequest request) {
        return ResponseEntity.status(exception.status())
                .body(error(exception.status(), exception.getMessage(), request));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Object> integrity(Exception exception, WebRequest request) {
        return ResponseEntity.status(409).body(error(HttpStatus.CONFLICT, "Нарушено ограничение целостности БД", request));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> authentication(AuthenticationException exception, WebRequest request) {
        return ResponseEntity.status(401).body(error(HttpStatus.UNAUTHORIZED, "Неверные учётные данные", request));
    }

    @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
    public ResponseEntity<Object> busy(Exception exception, WebRequest request) {
        return ResponseEntity.status(409).body(error(HttpStatus.CONFLICT, "База занята; повторите запрос", request));
    }

    /** Details stay in the log; the client sees only a generic message. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception exception, WebRequest request) throws Exception {
        // Spring Security's filter chain turns these into 401/403.
        if (exception instanceof AccessDeniedException || exception instanceof AuthenticationException) throw exception;
        log.error("Unexpected error", exception);
        return ResponseEntity.status(500).body(error(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера", request));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = switch (status.value()) {
            case 400 -> "Некорректный JSON, тип параметра или неизвестное поле запроса";
            case 404 -> "Ресурс не найден";
            case 405 -> "HTTP-метод не поддерживается для этого адреса";
            case 415 -> "Используйте Content-Type: application/json";
            default -> "Запрос не может быть обработан";
        };
        return new ResponseEntity<>(error(status, message, request), headers, status);
    }

    private ApiError error(HttpStatusCode status, String message, WebRequest request) {
        HttpStatus known = HttpStatus.resolve(status.value());
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        return new ApiError(clock.instant(), status.value(),
                known == null ? "Error" : known.getReasonPhrase(), message, path);
    }
}
