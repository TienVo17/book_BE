package com.example.book_be.shared.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;

@Component
public class ApiErrorWriter {
    private static final Logger log = LoggerFactory.getLogger(ApiErrorWriter.class);

    private final ObjectMapper objectMapper;

    public ApiErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ApiError create(HttpServletRequest request, int status, String code, String message) {
        ApiError error = new ApiError(
                Instant.now(),
                status,
                code,
                message,
                request.getRequestURI(),
                RequestTraceFilter.currentTraceId(request)
        );
        logFailure(request, error);
        return error;
    }

    private void logFailure(HttpServletRequest request, ApiError error) {
        log.warn("event=api_failure traceId={} method={} path={} status={} code={}",
                error.traceId(), request.getMethod(), request.getRequestURI(), error.status(), error.code());
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), create(request, status, code, message));
    }
}
