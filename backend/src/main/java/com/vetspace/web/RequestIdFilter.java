package com.vetspace.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request an id, puts it in the logging context, and echoes it back.
 *
 * <p>The problem this solves is a support one. A student reports "it failed when I
 * clicked validate" — with no shared identifier, finding that request in the log means
 * guessing from a timestamp and an endpoint, across every other student doing the same
 * thing at the same minute. With an id on the error toast, they quote six words and the
 * exact request is one grep away.
 *
 * <p>Runs at highest precedence, ahead of the Spring Security chain, so 401s and 429s —
 * the errors most likely to be reported — carry the header too. Anything that fails
 * before this filter has no id, but at that point nothing has been logged either.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";

    /** MDC key; {@code logging.pattern} in application.yml prints it on every line. */
    public static final String MDC_KEY = "requestId";

    /**
     * Inbound ids are echoed so a trace survives a proxy or a retry, but they are not
     * trusted blindly: this value reaches the log file, and an unbounded caller-supplied
     * string is a log-injection and log-bloat vector. Anything that is not a short,
     * boring token is replaced rather than rejected — the caller does not need to know,
     * and failing a request over a cosmetic header would be absurd.
     */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = sanitise(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        // Set before the chain runs: a downstream filter or handler may commit the
        // response, and headers added after that are silently dropped.
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Threads are pooled and reused. Without this, the next request handled by
            // this thread inherits a stale id and the log lies about which request it
            // belongs to — worse than having no id at all.
            MDC.remove(MDC_KEY);
        }
    }

    private static String sanitise(String candidate) {
        if (candidate != null && SAFE_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return newId();
    }

    /** Compact and case-preserving: a UUID without dashes, short enough to read aloud. */
    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** The current request's id, or {@code null} outside a request. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
