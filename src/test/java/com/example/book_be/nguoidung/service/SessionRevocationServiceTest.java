package com.example.book_be.nguoidung.service;

import com.example.book_be.nguoidung.baomat.JwtService;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.dto.PhanQuyenBo;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.session.RefreshSessionService;
import com.example.book_be.shared.config.FrontendUrlProvider;
import com.example.book_be.shared.email.EmailService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionRevocationServiceTest {
    @Test
    void password_change_and_reset_revoke_all_sessions_after_successful_save() {
        NguoiDungRepository users = mock(NguoiDungRepository.class);
        RefreshSessionService sessions = mock(RefreshSessionService.class);
        BCryptPasswordEncoder passwords = mock(BCryptPasswordEncoder.class);
        TaiKhoanService service = accountService(
                users, sessions, passwords, mock(JwtService.class));
        NguoiDung user = user();
        when(users.findByTenDangNhapForAuthWrite("alice")).thenReturn(Optional.of(user));
        when(passwords.matches("old", "hash")).thenReturn(true);
        when(passwords.encode("new")).thenReturn("new-hash");

        service.doiMatKhau("alice", "old", "new");
        verify(sessions).revokeAllByUser(42);

        user.setResetPasswordToken("reset-token");
        user.setResetPasswordTokenExpiry(new Date(System.currentTimeMillis() + 60_000));
        when(users.findByEmailForAuthWrite("alice@example.test")).thenReturn(Optional.of(user));
        service.datLaiMatKhau("alice@example.test", "reset-token", "new");
        verify(sessions, org.mockito.Mockito.times(2)).revokeAllByUser(42);
    }

    @Test
    void failed_password_validation_does_not_revoke_sessions() {
        NguoiDungRepository users = mock(NguoiDungRepository.class);
        RefreshSessionService sessions = mock(RefreshSessionService.class);
        BCryptPasswordEncoder passwords = mock(BCryptPasswordEncoder.class);
        when(users.findByTenDangNhapForAuthWrite("alice"))
                .thenReturn(Optional.of(user()));
        when(passwords.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> accountService(users, sessions, passwords, mock(JwtService.class))
                .doiMatKhau("alice", "wrong", "new")).isInstanceOf(RuntimeException.class);
        verify(sessions, never()).revokeAllByUser(42);
    }

    @Test
    void authentication_write_locks_user_before_issuing_complete_session() {
        NguoiDungRepository users = mock(NguoiDungRepository.class);
        RefreshSessionService sessions = mock(RefreshSessionService.class);
        BCryptPasswordEncoder passwords = mock(BCryptPasswordEncoder.class);
        JwtService jwt = mock(JwtService.class);
        NguoiDung user = user();
        when(users.findByTenDangNhapForAuthWrite("alice"))
                .thenReturn(Optional.of(user));
        when(passwords.matches("password", "hash")).thenReturn(true);
        when(jwt.generateToken(user)).thenReturn("access-token");

        TaiKhoanService.AuthenticatedSession authenticated = accountService(
                users, sessions, passwords, jwt)
                .authenticateAndIssueSession("alice", "password", true);

        verify(users).findByTenDangNhapForAuthWrite("alice");
        verify(sessions).issueIfEnabled(user, true);
        verify(jwt).generateToken(user);
        org.assertj.core.api.Assertions.assertThat(authenticated.accessToken())
                .isEqualTo("access-token");
    }

    @Test
    void reset_token_is_revalidated_under_same_user_lock() {
        NguoiDungRepository users = mock(NguoiDungRepository.class);
        RefreshSessionService sessions = mock(RefreshSessionService.class);
        BCryptPasswordEncoder passwords = mock(BCryptPasswordEncoder.class);
        NguoiDung user = user();
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiry(null);
        when(users.findByEmailForAuthWrite("alice@example.test"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> accountService(users, sessions, passwords, mock(JwtService.class))
                .datLaiMatKhau("alice@example.test", "already-used", "new"))
                .isInstanceOf(RuntimeException.class);

        verify(sessions, never()).revokeAllByUser(42);
    }

    @Test
    void successful_role_mutation_revokes_all_user_sessions() {
        EntityManager entityManager = mock(EntityManager.class);
        Query delete = mock(Query.class);
        Query insert = mock(Query.class);
        when(entityManager.createNativeQuery(org.mockito.ArgumentMatchers.startsWith("DELETE")))
                .thenReturn(delete);
        when(entityManager.createNativeQuery(org.mockito.ArgumentMatchers.startsWith("INSERT")))
                .thenReturn(insert);
        when(delete.setParameter("maNguoiDung", 42)).thenReturn(delete);
        when(insert.setParameter("maNguoiDung", 42)).thenReturn(insert);
        NguoiDungRepository users = mock(NguoiDungRepository.class);
        when(users.findByIdForAuthWrite(42)).thenReturn(Optional.of(user()));
        when(insert.setParameter("maQuyen", 1)).thenReturn(insert);
        RefreshSessionService sessions = mock(RefreshSessionService.class);
        AdminUserServiceImpl service = new AdminUserServiceImpl();
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        ReflectionTestUtils.setField(service, "nguoiDungRepository", users);
        ReflectionTestUtils.setField(service, "refreshSessionService", sessions);
        PhanQuyenBo request = new PhanQuyenBo();
        request.setUserId(42);
        request.setQuyenIds(List.of(1));

        service.phanQuyen(request);

        verify(users).findByIdForAuthWrite(42);
        verify(sessions).revokeAllByUser(42);
    }

    @Test
    void role_mutation_failure_is_not_swallowed_and_does_not_revoke() {
        EntityManager entityManager = mock(EntityManager.class);
        Query delete = mock(Query.class);
        when(entityManager.createNativeQuery(org.mockito.ArgumentMatchers.startsWith("DELETE")))
                .thenReturn(delete);
        when(delete.setParameter("maNguoiDung", 42)).thenReturn(delete);
        doThrow(new IllegalStateException("database failure")).when(delete).executeUpdate();
        RefreshSessionService sessions = mock(RefreshSessionService.class);
        NguoiDungRepository users = mock(NguoiDungRepository.class);
        when(users.findByIdForAuthWrite(42)).thenReturn(Optional.of(user()));
        AdminUserServiceImpl service = new AdminUserServiceImpl();
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        ReflectionTestUtils.setField(service, "nguoiDungRepository", users);
        ReflectionTestUtils.setField(service, "refreshSessionService", sessions);
        PhanQuyenBo request = new PhanQuyenBo();
        request.setUserId(42);
        request.setQuyenIds(List.of(1));

        assertThatThrownBy(() -> service.phanQuyen(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database failure");
        verify(sessions, never()).revokeAllByUser(42);
    }

    private TaiKhoanService accountService(NguoiDungRepository users,
                                           RefreshSessionService sessions,
                                           BCryptPasswordEncoder passwords,
                                           JwtService jwt) {
        TaiKhoanService service = new TaiKhoanService();
        ReflectionTestUtils.setField(service, "nguoiDungRepository", users);
        ReflectionTestUtils.setField(service, "refreshSessionService", sessions);
        ReflectionTestUtils.setField(service, "bCryptPasswordEncoder", passwords);
        ReflectionTestUtils.setField(service, "jwtService", jwt);
        ReflectionTestUtils.setField(service, "emailService", mock(EmailService.class));
        ReflectionTestUtils.setField(service, "frontendUrlProvider",
                new FrontendUrlProvider("https://frontend.example"));
        return service;
    }

    private NguoiDung user() {
        NguoiDung user = new NguoiDung();
        user.setMaNguoiDung(42);
        user.setTenDangNhap("alice");
        user.setEmail("alice@example.test");
        user.setMatKhau("hash");
        user.setDaKichHoat(true);
        return user;
    }
}
