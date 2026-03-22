package backend.academy.linktracker.bot.api.rest.dto;

import java.util.List;

/**
 * Contract error payload returned by bot REST API.
 *
 * @param description human-readable short error description
 * @param code machine-readable status code
 * @param exceptionName exception simple class name
 * @param exceptionMessage exception message
 * @param stacktrace serialized stack trace lines
 */
public record ApiErrorResponse(
        String description, String code, String exceptionName, String exceptionMessage, List<String> stacktrace) {}
