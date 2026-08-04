package com.example.book_be.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class RequestTraceFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Trace-Id";
    public static final String REQUEST_ATTRIBUTE = RequestTraceFilter.class.getName() + ".traceId";
    public static final String MDC_KEY = "traceId";

    private static final int MAX_TRACE_ID_LENGTH = 64;
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("^[A-Za-z0-9._-]+$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(HEADER_NAME));
        request.setAttribute(REQUEST_ATTRIBUTE, traceId);
        response.setHeader(HEADER_NAME, traceId);
        MDC.put(MDC_KEY, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    static String resolveTraceId(String candidate) {
        if (candidate != null && !candidate.isBlank()
                && candidate.length() <= MAX_TRACE_ID_LENGTH
                && SAFE_TRACE_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }

    public static String currentTraceId(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ATTRIBUTE);
        if (value instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }
        String mdcTraceId = MDC.get(MDC_KEY);
        return mdcTraceId == null || mdcTraceId.isBlank() ? UUID.randomUUID().toString() : mdcTraceId;
    }
}
