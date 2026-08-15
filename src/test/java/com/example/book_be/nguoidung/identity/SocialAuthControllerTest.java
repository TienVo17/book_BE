package com.example.book_be.nguoidung.identity;

import com.example.book_be.nguoidung.domain.NguoiDung;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SocialAuthControllerTest {
    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth?state=abc";

    private SocialAuthService socialAuthService;
    private SocialProviderProperties properties;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        socialAuthService = mock(SocialAuthService.class);
        properties = new SocialProviderProperties(true, "client", "secret",
                "https://tienvo17.vercel.app/tai-khoan/oauth/google/callback");
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SocialAuthController(socialAuthService, properties))
                .build();
    }

    private MockMvc disabledMockMvc() {
        SocialProviderProperties disabled = new SocialProviderProperties(false, "", "", "");
        return MockMvcBuilders
                .standaloneSetup(new SocialAuthController(socialAuthService, disabled))
                .build();
    }

    @Test
    void start_redirects_to_the_provider_and_sets_the_browser_binding_cookie() throws Exception {
        when(socialAuthService.startLogin())
                .thenReturn(new SocialAuthService.Authorization(AUTH_URL, "binding-1"));

        MockHttpServletResponse response = mockMvc.perform(get("/tai-khoan/oauth/google/start"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, AUTH_URL))
                .andReturn().getResponse();

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains(SocialAuthController.BINDING_COOKIE);
        // Binding la bi mat cua luong dang nhap: JavaScript khong duoc doc, va no phai di
        // kem redirect quay ve tu Google nen SameSite khong the la Strict.
        assertThat(setCookie).contains("HttpOnly").contains("Secure").contains("SameSite=Lax");
    }

    /** Auth responses must never be cached by a CDN or the browser. */
    @Test
    void start_is_never_cacheable() throws Exception {
        when(socialAuthService.startLogin())
                .thenReturn(new SocialAuthService.Authorization(AUTH_URL, "binding-1"));

        mockMvc.perform(get("/tai-khoan/oauth/google/start"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"));
    }

    @Test
    void a_disabled_provider_answers_404_rather_than_revealing_the_route() throws Exception {
        disabledMockMvc().perform(get("/tai-khoan/oauth/google/start"))
                .andExpect(status().isNotFound());

        verify(socialAuthService, never()).startLogin();
    }

    @Test
    void disabled_provider_status_reports_google_as_unavailable() throws Exception {
        disabledMockMvc().perform(get("/tai-khoan/oauth/trang-thai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.google").value(false));
    }

    @Test
    void enabled_provider_status_reports_google_as_available() throws Exception {
        mockMvc.perform(get("/tai-khoan/oauth/trang-thai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.google").value(true));
    }

    @Test
    void callback_for_a_linked_account_redirects_to_the_result_page() throws Exception {
        NguoiDung user = new NguoiDung();
        user.setMaNguoiDung(7);
        when(socialAuthService.completeCallback(anyString(), anyString(), anyString()))
                .thenReturn(new SocialAuthService.CallbackResult(
                        SocialAuthService.Outcome.AUTHENTICATED, user, null, null));

        mockMvc.perform(get("/tai-khoan/oauth/google/callback")
                        .param("code", "code-1")
                        .param("state", "state-1")
                        .cookie(new Cookie(SocialAuthController.BINDING_COOKIE, "binding-1")))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.startsWith("/tai-khoan/oauth/ket-qua")));
    }

    /**
     * A callback that arrives without the binding cookie cannot prove it belongs to the browser
     * that started the flow, so it must never reach the token exchange.
     */
    @Test
    void callback_without_the_binding_cookie_is_rejected() throws Exception {
        mockMvc.perform(get("/tai-khoan/oauth/google/callback")
                        .param("code", "code-1")
                        .param("state", "state-1"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.containsString("loi")));

        verify(socialAuthService, never()).completeCallback(anyString(), anyString(), anyString());
    }

    /** A user who declines consent is not an error to shout about; send them home quietly. */
    @Test
    void a_denied_consent_redirects_without_calling_the_token_exchange() throws Exception {
        mockMvc.perform(get("/tai-khoan/oauth/google/callback")
                        .param("error", "access_denied")
                        .param("state", "state-1")
                        .cookie(new Cookie(SocialAuthController.BINDING_COOKIE, "binding-1")))
                .andExpect(status().isFound());

        verify(socialAuthService, never()).completeCallback(anyString(), anyString(), anyString());
    }

    /**
     * The redirect target is the one place an attacker could inject an absolute URL, so the
     * result location must always stay on this origin.
     */
    @Test
    void the_callback_never_redirects_off_origin() throws Exception {
        when(socialAuthService.completeCallback(anyString(), anyString(), anyString()))
                .thenThrow(new AuthIdentityException("OAUTH_STATE_INVALID"));

        MockHttpServletResponse response = mockMvc.perform(get("/tai-khoan/oauth/google/callback")
                        .param("code", "code-1")
                        .param("state", "bad")
                        .param("returnTo", "https://evil.example/steal")
                        .cookie(new Cookie(SocialAuthController.BINDING_COOKIE, "binding-1")))
                .andExpect(status().isFound())
                .andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.LOCATION)).startsWith("/");
    }

    /** The binding cookie is single-use; leaving it set would let a later callback reuse it. */
    @Test
    void the_binding_cookie_is_cleared_once_the_callback_completes() throws Exception {
        when(socialAuthService.completeCallback(anyString(), anyString(), anyString()))
                .thenReturn(new SocialAuthService.CallbackResult(
                        SocialAuthService.Outcome.SIGNUP_REQUIRED, null, null, null));

        MockHttpServletResponse response = mockMvc.perform(get("/tai-khoan/oauth/google/callback")
                        .param("code", "code-1")
                        .param("state", "state-1")
                        .cookie(new Cookie(SocialAuthController.BINDING_COOKIE, "binding-1")))
                .andReturn().getResponse();

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(cookie -> assertThat(cookie)
                        .contains(SocialAuthController.BINDING_COOKIE)
                        .contains("Max-Age=0"));
    }

    @Test
    void an_error_code_never_leaks_into_the_redirect_as_free_text() throws Exception {
        when(socialAuthService.completeCallback(anyString(), anyString(), anyString()))
                .thenThrow(new AuthIdentityException("OAUTH_TOKEN_INVALID"));

        MockHttpServletResponse response = mockMvc.perform(get("/tai-khoan/oauth/google/callback")
                        .param("code", "code-1")
                        .param("state", "state-1")
                        .cookie(new Cookie(SocialAuthController.BINDING_COOKIE, "binding-1")))
                .andReturn().getResponse();

        String location = response.getHeader(HttpHeaders.LOCATION);
        assertThat(location).doesNotContain("code-1");
    }
}
