package com.example.book_be.shared.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestTraceFilterTest {
    private final RequestTraceFilter filter = new RequestTraceFilter();

    @Test
    void accepts_bounded_safe_trace_id_and_clears_mdc_after_request() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader(RequestTraceFilter.HEADER_NAME, "trace-safe_123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            assertThat(MDC.get(RequestTraceFilter.MDC_KEY)).isEqualTo("trace-safe_123");
            assertThat(req.getAttribute(RequestTraceFilter.REQUEST_ATTRIBUTE)).isEqualTo("trace-safe_123");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestTraceFilter.HEADER_NAME)).isEqualTo("trace-safe_123");
        assertThat(MDC.get(RequestTraceFilter.MDC_KEY)).isNull();
    }

    @Test
    void replaces_invalid_or_oversized_trace_id() throws Exception {
        for (String unsafe : new String[]{"has spaces", "line\nbreak", "a".repeat(65)}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
            request.addHeader(RequestTraceFilter.HEADER_NAME, unsafe);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (req, res) -> { });

            String generated = response.getHeader(RequestTraceFilter.HEADER_NAME);
            assertThat(generated).isNotBlank().isNotEqualTo(unsafe);
            assertThat(generated).matches("^[A-Za-z0-9._-]+$");
        }
    }

    @Test
    void clears_mdc_even_when_downstream_throws() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> filter.doFilter(request, response, (req, res) -> {
                    throw new IllegalStateException("controlled test failure");
                }));

        assertThat(MDC.get(RequestTraceFilter.MDC_KEY)).isNull();
    }
}
