package com.example.book_be.shared.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CartMergeRequestSizeFilterTest {

    private final CartMergeRequestSizeFilter filter =
            new CartMergeRequestSizeFilter(new ApiErrorWriter(
                    new ObjectMapper().findAndRegisterModules()));

    @Test
    void tu_choi_merge_json_vuot_gioi_han_truoc_mvc_binding() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/gio-hang/merge");
        request.setContent(new byte[
                CartMergeRequestSizeFilter.MAX_CONTENT_LENGTH_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString())
                .contains("PAYLOAD_TOO_LARGE");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void cho_merge_json_trong_gioi_han_di_tiep() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/gio-hang/merge");
        request.setContent(new byte[512]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
