package com.vetspace.web.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vetspace.web.RequestIdFilter;
import java.time.Instant;

/**
 * The one error shape, per CLAUDE.md: {@code {error, message, timestamp}} — plus
 * {@code requestId}, so the response a student can see and the log line an admin has to
 * find share an identifier.
 *
 * @param requestId correlation id for this request; omitted from the JSON when absent
 *                  (an error raised outside a request has nothing to correlate)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String error, String message, Instant timestamp, String requestId) {

    /**
     * Fills the request id from the logging context.
     *
     * <p>Every existing call site passes three arguments and should not have to thread
     * the id through by hand — a correlation id each caller must remember to pass is one
     * that goes missing exactly where it matters.
     */
    public ApiError(String error, String message, Instant timestamp) {
        this(error, message, timestamp, RequestIdFilter.current());
    }
}
