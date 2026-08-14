package com.example.book_be.nguoidung.session;

import com.example.book_be.nguoidung.baomat.JwtService;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.domain.Quyen;
import com.example.book_be.nguoidung.service.TaiKhoanService;
import com.example.book_be.nguoidung.service.UserService;
import org.junit.jupiter.api.Test;
import com.example.book_be.shared.web.ApiErrorWriter;
import com.example.book_be.shared.web.ApiExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaiKhoanSessionControllerTest {
    @Test
    void refresh_rotates_cookie_and_returns_compatible_access_contract() throws Exception {
        Fixture fixture = fixture(true);
        when(fixture.sessions.rotateAndIssueAccessToken("selector.secret")).thenReturn(
                new TaiKhoanService.AuthenticatedSession(
                        fixture.user,
                        new RefreshSessionService.SessionGrant("next.selector", fixture.user, true,
                                Instant.parse("2026-09-13T00:00:00Z")),
                        "access-token"));
        when(fixture.jwt.getExpirationSeconds()).thenReturn(900L);

        fixture.mvc.perform(post("/tai-khoan/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(RefreshCookieFactory.COOKIE_NAME,
                                "selector.secret")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("Set-Cookie", containsString("__Host-refresh=next.selector")))
                .andExpect(jsonPath("$.jwt").value("access-token"))
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.principal.uid").value(42))
                .andExpect(jsonPath("$.principal.username").value("alice"))
                .andExpect(jsonPath("$.principal.roles[0]").value("USER"));
    }

    @Test
    void invalid_refresh_uses_common_error_envelope_and_clears_cookie() throws Exception {
        Fixture fixture = fixture(true);
        when(fixture.sessions.rotateAndIssueAccessToken("used.selector"))
                .thenThrow(new RefreshSessionException("REAUTHENTICATION_REQUIRED"));

        fixture.mvc.perform(post("/tai-khoan/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(
                                RefreshCookieFactory.COOKIE_NAME, "used.selector")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("REAUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.path").value("/tai-khoan/refresh"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void logout_is_idempotent_and_always_clears_cookie() throws Exception {
        Fixture fixture = fixture(true);

        fixture.mvc.perform(post("/tai-khoan/dang-xuat"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        verify(fixture.sessions).revokeCurrent(null);
    }

    @Test
    void session_endpoint_requires_bearer_principal_metadata_not_refresh_cookie() throws Exception {
        Fixture fixture = fixture(true);
        when(fixture.users.findByUsername("alice")).thenReturn(fixture.user);

        fixture.mvc.perform(get("/tai-khoan/phien")
                        .principal(new UsernamePasswordAuthenticationToken("alice", null, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal.uid").value(42));
    }

    @Test
    void feature_off_returns_not_found_without_touching_session_store() throws Exception {
        Fixture fixture = fixture(false);
        fixture.mvc.perform(get("/tai-khoan/csrf")).andExpect(status().isNotFound());
        fixture.mvc.perform(post("/tai-khoan/refresh")).andExpect(status().isNotFound());
    }

    private Fixture fixture(boolean enabled) {
        RefreshSessionService sessions = mock(RefreshSessionService.class);
        when(sessions.isEnabled()).thenReturn(enabled);
        JwtService jwt = mock(JwtService.class);
        UserService users = mock(UserService.class);
        NguoiDung user = new NguoiDung();
        user.setMaNguoiDung(42);
        user.setTenDangNhap("alice");
        user.setDaKichHoat(true);
        Quyen role = new Quyen();
        role.setTenQuyen("USER");
        user.setDanhSachQuyen(List.of(role));
        TaiKhoanSessionController controller = new TaiKhoanSessionController(
                sessions, new RefreshCookieFactory(),
                new AuthCsrfTokenService("release-one-test-csrf-hmac-key-at-least-32-bytes", true),
                jwt, users);
        ApiExceptionHandler exceptionHandler = new ApiExceptionHandler(
                new ApiErrorWriter(new ObjectMapper()));
        return new Fixture(MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(exceptionHandler)
                .addFilter(new AuthNoStoreFilter())
                .build(), sessions, jwt, users, user);
    }

    private record Fixture(MockMvc mvc, RefreshSessionService sessions, JwtService jwt,
                           UserService users, NguoiDung user) {}
}
