package com.example.book_be.nguoidung.session;

import com.example.book_be.shared.config.FrontendUrlProvider;
import com.example.book_be.shared.web.ApiError;
import com.example.book_be.shared.web.ApiErrorWriter;
import com.example.book_be.shared.web.RequestTraceFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuthOriginCsrfFilterTest {
    private static final String KEY = "release-one-test-csrf-hmac-key-at-least-32-bytes";
    private final AuthCsrfTokenService csrf = new AuthCsrfTokenService(KEY, true);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final AuthOriginCsrfFilter filter = new AuthOriginCsrfFilter(
            new FrontendUrlProvider("https://tienvo17.vercel.app"), csrf,
            new ApiErrorWriter(objectMapper), true);

    @Test
    void accepts_exact_origin_and_signed_double_submit_for_refresh() throws Exception {
        String token = csrf.issueToken();
        MockHttpServletRequest request = request("/tai-khoan/refresh");
        request.addHeader("Origin", "https://tienvo17.vercel.app");
        request.addHeader(AuthOriginCsrfFilter.CSRF_HEADER, token);
        request.setCookies(new jakarta.servlet.http.Cookie(AuthCsrfTokenService.COOKIE_NAME, token));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, private");
    }

    @Test
    void rejects_missing_origin_or_mismatched_csrf_fail_closed() throws Exception {
        String token = csrf.issueToken();
        MockHttpServletRequest missingOrigin = request("/tai-khoan/refresh");
        missingOrigin.setAttribute(RequestTraceFilter.REQUEST_ATTRIBUTE, "trace-auth-origin");
        missingOrigin.addHeader(AuthOriginCsrfFilter.CSRF_HEADER, token);
        missingOrigin.setCookies(new jakarta.servlet.http.Cookie(AuthCsrfTokenService.COOKIE_NAME, token));
        MockHttpServletResponse first = new MockHttpServletResponse();
        filter.doFilter(missingOrigin, first, mock(FilterChain.class));
        assertThat(first.getStatus()).isEqualTo(403);
        ApiError originError = objectMapper.readValue(first.getContentAsByteArray(), ApiError.class);
        assertThat(originError.status()).isEqualTo(403);
        assertThat(originError.code()).isEqualTo("AUTH_ORIGIN_REJECTED");
        assertThat(originError.path()).isEqualTo("/tai-khoan/refresh");
        assertThat(originError.traceId()).isEqualTo("trace-auth-origin");

        MockHttpServletRequest mismatch = request("/tai-khoan/dang-xuat");
        mismatch.setAttribute(RequestTraceFilter.REQUEST_ATTRIBUTE, "trace-auth-csrf");
        mismatch.addHeader("Referer", "https://tienvo17.vercel.app/account");
        mismatch.addHeader(AuthOriginCsrfFilter.CSRF_HEADER, token);
        mismatch.setCookies(new jakarta.servlet.http.Cookie(AuthCsrfTokenService.COOKIE_NAME, "different"));
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(mismatch, second, mock(FilterChain.class));
        assertThat(second.getStatus()).isEqualTo(403);
        ApiError csrfError = objectMapper.readValue(second.getContentAsByteArray(), ApiError.class);
        assertThat(csrfError.status()).isEqualTo(403);
        assertThat(csrfError.code()).isEqualTo("AUTH_CSRF_REJECTED");
        assertThat(csrfError.path()).isEqualTo("/tai-khoan/dang-xuat");
        assertThat(csrfError.traceId()).isEqualTo("trace-auth-csrf");
    }

    @Test
    void rejects_conflicting_referer_even_when_origin_is_valid() throws Exception {
        String token = csrf.issueToken();
        MockHttpServletRequest request = request("/tai-khoan/refresh");
        request.addHeader("Origin", "https://tienvo17.vercel.app");
        request.addHeader("Referer", "https://untrusted.example/account");
        request.addHeader(AuthOriginCsrfFilter.CSRF_HEADER, token);
        request.setCookies(new jakarta.servlet.http.Cookie(AuthCsrfTokenService.COOKIE_NAME, token));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("AUTH_ORIGIN_REJECTED");
    }

    @Test
    void feature_on_does_not_block_legacy_login_without_csrf_bootstrap() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/tai-khoan/dang-nhap");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void refresh_cookie_does_not_create_authentication_or_filter_business_api() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/gio-hang");
        request.addHeader("Cookie", RefreshCookieFactory.COOKIE_NAME + "=selector.secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    private MockHttpServletRequest request(String path) {
        return new MockHttpServletRequest("POST", path);
    }
}
