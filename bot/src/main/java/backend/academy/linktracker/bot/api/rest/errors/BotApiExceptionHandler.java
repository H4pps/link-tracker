package backend.academy.linktracker.bot.api.rest.errors;

import backend.academy.linktracker.bot.api.rest.errors.dto.ApiErrorResponse;
import backend.academy.linktracker.bot.logging.BotLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Converts bot API exceptions into contract-compliant error responses.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class BotApiExceptionHandler {

    private static final String BAD_REQUEST_DESCRIPTION = "Некорректные параметры запроса";
    private static final String INTERNAL_ERROR_DESCRIPTION = "Внутренняя ошибка сервиса";

    private final BotLogger botLogger;

    /**
     * Handles request validation and JSON parsing failures.
     *
     * @param exception source exception
     * @param request HTTP request metadata
     * @return contract-shaped bad request response
     */
    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HandlerMethodValidationException.class,
        BindException.class,
        ConstraintViolationException.class,
        MissingRequestValueException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, BAD_REQUEST_DESCRIPTION, exception, request.getRequestURI());
    }

    /**
     * Handles unexpected runtime failures.
     *
     * @param exception source exception
     * @param request HTTP request metadata
     * @return internal error response
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(RuntimeException exception, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_DESCRIPTION, exception, request.getRequestURI());
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status, String description, Exception exception, String endpoint) {
        botLogger.logApiRequestFailed(endpoint, status.value(), status.name(), exception);
        ApiErrorResponse body = new ApiErrorResponse(
                description,
                status.name(),
                exception.getClass().getSimpleName(),
                exception.getMessage(),
                toStacktrace(exception));
        return ResponseEntity.status(status).body(body);
    }

    private List<String> toStacktrace(Exception exception) {
        return Arrays.stream(exception.getStackTrace())
                .map(StackTraceElement::toString)
                .toList();
    }
}
