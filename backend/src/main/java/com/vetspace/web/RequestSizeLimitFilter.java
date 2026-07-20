package com.vetspace.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetspace.web.error.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Caps non-multipart request bodies at 2MB (multipart/media uploads are governed separately by
 * spring.servlet.multipart, which allows up to the 4MB media limit). Oversized bodies are rejected
 * up front with the standard error shape so no controller ever buffers a huge payload.
 */
@Component
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    static final long MAX_BODY_BYTES = 2L * 1024 * 1024;

    private final ObjectMapper objectMapper;

    public RequestSizeLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String contentType = request.getContentType();
        boolean multipart = contentType != null && contentType.toLowerCase().startsWith("multipart/");
        if (!multipart && request.getContentLengthLong() > MAX_BODY_BYTES) {
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiError error = new ApiError(HttpStatus.PAYLOAD_TOO_LARGE.getReasonPhrase(),
                "Request body exceeds the 2MB limit", Instant.now());
            response.getWriter().write(objectMapper.writeValueAsString(error));
            return;
        }
        chain.doFilter(request, response);
    }
}
