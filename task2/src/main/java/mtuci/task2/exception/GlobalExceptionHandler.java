package mtuci.task2.exception;

import java.time.Clock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApi(ApiException exception, WebRequest request) {
        return ResponseEntity.status(exception.status())
                .body(error(exception.status(), exception.getMessage(), request));
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
