package com.example.book_be.nguoidung.identity;

import com.example.book_be.nguoidung.baomat.JwtService;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.session.RefreshCookieFactory;
import com.example.book_be.nguoidung.session.RefreshSessionService;
import com.example.book_be.shared.email.EmailService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SocialAuthControllerTest {
    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth?state=abc";

    private SocialAuthService socialAuthService;
    private SocialProviderProperties properties;
    private SocialSignupIntentService intentService;
    private SocialSignupService signupService;
    private RefreshSessionService refreshSessionService;
    private RefreshCookieFactory refreshCookieFactory;
    private JwtService jwtService;
    private EmailService emailService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        socialAuthService = mock(SocialAuthService.class);
        intentService = mock(SocialSignupIntentService.class);
        signupService = mock(SocialSignupService.class);
        refreshSessionService = mock(RefreshSessionService.class);
        refreshCookieFactory = mock(RefreshCookieFactory.class);
        jwtService = mock(JwtService.class);
        emailService = mock(EmailService.class);
        properties = new SocialProviderProperties(true, "client", "secret",
                "https://tienvo17.vercel.app/tai-khoan/oauth/google/callback");
        mockMvc = MockMvcBuilders.standaloneSetup(controller(properties)).build();
    }

    private SocialAuthController controller(SocialProviderProperties config) {
        return new SocialAuthController(socialAuthService, config, intentService, signupService,
                refreshSessionService, refreshCookieFactory, jwtService, emailService);
    }

    private MockMvc disabledMockMvc() {
        SocialProviderProperties disabled = new SocialProviderProperties(false, "", "", "");
        return MockMvcBuilders.standaloneSetup(controller(disabled)).build();
    }

    @Test
    void start_redirects_to_the_provider_and_sets_the_browser_binding_cookie() throws Exception {
        when(socialAuthService.startLogin("google"))
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
        when(socialAuthService.startLogin("google"))
                .thenReturn(new SocialAuthService.Authorization(AUTH_URL, "binding-1"));

        mockMvc.perform(get("/tai-khoan/oauth/google/start"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"));
    }

    @Test
    void a_disabled_provider_answers_404_rather_than_revealing_the_route() throws Exception {
        disabledMockMvc().perform(get("/tai-khoan/oauth/google/start"))
                .andExpect(status().isNotFound());

        verify(socialAuthService, never()).startLogin(anyString());
    }

    /** An invented provider name must not reach the service or reveal that the route exists. */
    @Test
    void an_unknown_provider_answers_404() throws Exception {
        mockMvc.perform(get("/tai-khoan/oauth/tiktok/start"))
                .andExpect(status().isNotFound());

        verify(socialAuthService, never()).startLogin(anyString());
    }

    @Test
    void facebook_start_is_404_while_that_provider_is_disabled() throws Exception {
        // properties trong setUp chi bat Google; Facebook van tat.
        mockMvc.perform(get("/tai-khoan/oauth/facebook/start"))
                .andExpect(status().isNotFound());

        verify(socialAuthService, never()).startLogin("facebook");
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

    /**
     * Distinguishing "variable never set" from "set but not reaching the app" is otherwise
     * guesswork, because a missing variable simply leaves the provider silently disabled.
     */
    @Test
    void status_reports_which_settings_the_application_actually_read() throws Exception {
        mockMvc.perform(get("/tai-khoan/oauth/trang-thai"))
                .andExpect(jsonPath("$.cauHinh.clientId").value(true))
                .andExpect(jsonPath("$.cauHinh.clientSecret").value(true))
                .andExpect(jsonPath("$.cauHinh.redirectUri").value(true));

        disabledMockMvc().perform(get("/tai-khoan/oauth/trang-thai"))
                .andExpect(jsonPath("$.cauHinh.clientId").value(false))
                .andExpect(jsonPath("$.cauHinh.clientSecret").value(false));
    }

    /**
     * The diagnostic must never turn into a way to read the settings themselves. Key names
     * are expected in the payload; the configured values are what must never appear.
     */
    @Test
    void status_never_exposes_configuration_values() throws Exception {
        String body = mockMvc.perform(get("/tai-khoan/oauth/trang-thai"))
                .andReturn().getResponse().getContentAsString();

        // Gia tri thuc dat trong setUp: "client", "secret" va URL redirect.
        assertThat(body).doesNotContain("\"client\"").doesNotContain("\"secret\"");
        assertThat(body).doesNotContain("tienvo17.vercel.app");
    }

    @Test
    void callback_for_a_linked_account_redirects_to_the_result_page() throws Exception {
        NguoiDung user = new NguoiDung();
        user.setMaNguoiDung(7);
        when(socialAuthService.completeCallback(anyString(), anyString(), anyString(), anyString()))
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
     * Danh tinh da xac minh phai duoc giao lai cho buoc hoan tat, neu khong thi callback chi
     * hien duoc mot trang "can dang ky" khong dan di dau - moi lan dang nhap deu ket thuc o
     * do vi khong co gi tao ra `auth_identity`.
     */
    @Test
    void a_signup_callback_hands_the_verified_identity_to_the_completion_step() throws Exception {
        ProviderIdentity identity = new ProviderIdentity("google", "https://accounts.google.com",
                "sub-1", "nguoi@example.com", true, "Tien");
        when(socialAuthService.completeCallback(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new SocialAuthService.CallbackResult(
                        SocialAuthService.Outcome.SIGNUP_REQUIRED, null, identity, null));
        when(intentService.create(identity)).thenReturn("intent-token-1");

        MockHttpServletResponse response = mockMvc.perform(get("/tai-khoan/oauth/google/callback")
                        .param("code", "code-1")
                        .param("state", "state-1")
                        .cookie(new Cookie(SocialAuthController.BINDING_COOKIE, "binding-1")))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.containsString("can-dang-ky")))
                .andReturn().getResponse();

        verify(intentService).create(identity);
        assertThat(String.join(";", response.getHeaders(HttpHeaders.SET_COOKIE)))
                .contains(SocialAuthController.INTENT_COOKIE)
                .contains("HttpOnly");
    }

    /** Ho so phai bi tieu truoc khi tao tai khoan: gui lai cung mot form khong duoc tao them. */
    @Test
    void completion_consumes_the_intent_before_creating_the_account() throws Exception {
        OAuthSignupIntent intent = new OAuthSignupIntent();
        intent.setProvider("google");
        NguoiDung created = new NguoiDung();
        created.setMaNguoiDung(11);
        created.setTenDangNhap("reader");
        when(intentService.consume("intent-token-1")).thenReturn(intent);
        when(signupService.complete(any(), any())).thenReturn(created);
        when(jwtService.generateToken(created)).thenReturn("access-1");

        mockMvc.perform(post("/tai-khoan/oauth/hoan-tat-dang-ky")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenDangNhap\":\"reader\",\"email\":\"nguoi@example.com\"}")
                        .cookie(new Cookie(SocialAuthController.INTENT_COOKIE, "intent-token-1")))
                .andExpect(status().isOk());

        InOrder order = inOrder(intentService, signupService);
        order.verify(intentService).consume("intent-token-1");
        order.verify(signupService).complete(any(), any());
    }

    /** Ma xac minh chi duoc di qua email. Tra ve trong phan hoi la bo qua han buoc xac minh. */
    @Test
    void the_email_code_never_travels_back_in_the_response() throws Exception {
        when(intentService.startEmailVerification(anyString(), anyString())).thenReturn("482915");

        String body = mockMvc.perform(post("/tai-khoan/oauth/gui-ma-xac-minh-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nguoi@example.com\"}")
                        .cookie(new Cookie(SocialAuthController.INTENT_COOKIE, "intent-token-1")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("482915");
        verify(emailService).sendEmail(org.mockito.ArgumentMatchers.eq("nguoi@example.com"),
                anyString(), anyString());
    }

    /**
     * Gui email that bai khong duoc doi phan hoi: mot phan hoi khac di la mot cong cu do xem
     * dia chi nao ton tai. Ma van nam trong ho so nen nguoi dung co the yeu cau gui lai.
     */
    @Test
    void a_failed_send_looks_exactly_like_a_successful_one() throws Exception {
        when(intentService.startEmailVerification(anyString(), anyString())).thenReturn("482915");
        org.mockito.Mockito.doThrow(new IllegalStateException("smtp down"))
                .when(emailService).sendEmail(anyString(), anyString(), anyString());

        mockMvc.perform(post("/tai-khoan/oauth/gui-ma-xac-minh-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nguoi@example.com\"}")
                        .cookie(new Cookie(SocialAuthController.INTENT_COOKIE, "intent-token-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daGui").value(true));
    }

    /** Ho so het han hoac khong co la loi cua nguoi goi, khong phai su co may chu. */
    @Test
    void a_missing_intent_answers_with_a_stable_code_not_a_server_error() throws Exception {
        when(intentService.require(null)).thenThrow(new AuthIdentityException("SIGNUP_INTENT_INVALID"));

        mockMvc.perform(get("/tai-khoan/oauth/dang-ky-cho"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SIGNUP_INTENT_INVALID"));
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

        verify(socialAuthService, never()).completeCallback(anyString(), anyString(), anyString(), anyString());
    }

    /** A user who declines consent is not an error to shout about; send them home quietly. */
    @Test
    void a_denied_consent_redirects_without_calling_the_token_exchange() throws Exception {
        mockMvc.perform(get("/tai-khoan/oauth/google/callback")
                        .param("error", "access_denied")
                        .param("state", "state-1")
                        .cookie(new Cookie(SocialAuthController.BINDING_COOKIE, "binding-1")))
                .andExpect(status().isFound());

        verify(socialAuthService, never()).completeCallback(anyString(), anyString(), anyString(), anyString());
    }

    /**
     * The redirect target is the one place an attacker could inject an absolute URL, so the
     * result location must always stay on this origin.
     */
    @Test
    void the_callback_never_redirects_off_origin() throws Exception {
        when(socialAuthService.completeCallback(anyString(), anyString(), anyString(), anyString()))
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
        when(socialAuthService.completeCallback(anyString(), anyString(), anyString(), anyString()))
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
        when(socialAuthService.completeCallback(anyString(), anyString(), anyString(), anyString()))
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
