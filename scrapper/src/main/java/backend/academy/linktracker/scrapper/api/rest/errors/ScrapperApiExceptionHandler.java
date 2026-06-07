package backend.academy.linktracker.scrapper.api.rest.errors;

import backend.academy.linktracker.scrapper.api.rest.errors.dto.ApiErrorResponse;
import backend.academy.linktracker.scrapper.domain.exception.AlreadyExistsException;
import backend.academy.linktracker.scrapper.domain.exception.NotFoundException;
import backend.academy.linktracker.scrapper.logging.ScrapperLogger;
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
 * Converts scrapper API exceptions into contract-compliant error responses.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class ScrapperApiExceptionHandler {

    private static final String BAD_REQUEST_DESCRIPTION = "Некорректные параметры запроса";
    private static final String NOT_FOUND_DESCRIPTION = "Ресурс не найден";
    private static final String CONFLICT_DESCRIPTION = "Конфликт состояния ресурса";
    private static final String TOO_MANY_REQUESTS_DESCRIPTION = "Превышен лимит запросов";
    private static final String INTERNAL_ERROR_DESCRIPTION = "Внутренняя ошибка сервиса";

    private final ScrapperLogger scrapperLogger;

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
     * Handles not-found domain failures.
     *
     * @param exception source exception
     * @param request HTTP request metadata
     * @return not-found response
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, NOT_FOUND_DESCRIPTION, exception, request.getRequestURI());
    }

    /**
     * Handles conflict domain failures.
     *
     * @param exception source exception
     * @param request HTTP request metadata
     * @return conflict response
     */
    @ExceptionHandler(AlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            AlreadyExistsException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, CONFLICT_DESCRIPTION, exception, request.getRequestURI());
    }

    /**
     * Handles REST rate-limit failures.
     *
     * @param exception source exception
     * @param request HTTP request metadata
     * @return too-many-requests response
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimitExceeded(
            RateLimitExceededException exception, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_REQUESTS_DESCRIPTION, exception, request.getRequestURI());
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
        scrapperLogger.logRequestFailed(endpoint, status.value(), status.name(), exception);
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
