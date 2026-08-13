package com.example.book_be.shared.web;

import com.example.book_be.shared.config.FrontendUrlProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/** Applies the rehearsal's non-cacheable response contract before Spring Security and CORS. */
public class ProxyRehearsalNoStoreFilter extends OncePerRequestFilter {
    private static final Set<String> REHEARSAL_PATHS = Set.of(
            ProxyRehearsalController.ISSUE_PATH,
            ProxyRehearsalController.REDIRECT_PATH,
            ProxyRehearsalController.COMPLETE_PATH
    );

    private final String frontendOrigin;

    public ProxyRehearsalNoStoreFilter(FrontendUrlProvider frontendUrlProvider) {
        this.frontendOrigin = frontendUrlProvider.getFrontendUrl();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !REHEARSAL_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        applyNoStoreHeaders(response);
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !frontendOrigin.equals(origin)) {
            writeRejectedOrigin(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeRejectedOrigin(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String traceId = RequestTraceFilter.currentTraceId(request);
        response.getWriter().write("{\"code\":\"PROXY_REHEARSAL_ORIGIN_REJECTED\","
                + "\"message\":\"Yêu cầu rehearsal không hợp lệ.\","
                + "\"traceId\":\"" + traceId + "\"}");
    }

    public static void applyNoStoreHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, private");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("CDN-Cache-Control", "no-store");
    }
}
