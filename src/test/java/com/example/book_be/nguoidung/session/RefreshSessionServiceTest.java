package com.example.book_be.nguoidung.session;

import com.example.book_be.nguoidung.baomat.JwtService;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshSessionServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-14T02:00:00Z");
    private static final String KEY = "release-one-test-refresh-hmac-key-at-least-32-bytes";

    @Mock RefreshTokenSessionRepository repository;

    @Test
    void issue_uses_same_hard_thirty_day_expiry_for_session_and_persistent_cookies() {
        RefreshTokenCodec codec = new RefreshTokenCodec(KEY);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        RefreshSessionService service = service(codec);

        RefreshSessionService.SessionGrant unchecked = service.issue(enabledUser(), false);
        RefreshSessionService.SessionGrant checked = service.issue(enabledUser(), true);

        assertThat(unchecked.absoluteExpiresAt()).isEqualTo(NOW.plusSeconds(2_592_000));
        assertThat(checked.absoluteExpiresAt()).isEqualTo(NOW.plusSeconds(2_592_000));
    }

    @Test
    void rotation_consumes_once_and_preserves_original_absolute_expiry() {
        RefreshTokenCodec codec = new RefreshTokenCodec(KEY);
        RefreshTokenCodec.IssuedToken original = codec.issue();
        NguoiDung user = enabledUser();
        RefreshTokenSession stored = session(original, user);
        when(repository.findBySelectorForUpdate(original.selector())).thenReturn(Optional.of(stored));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        RefreshSessionService service = service(codec);

        RefreshSessionService.SessionGrant rotated = service.rotate(original.rawToken());

        assertThat(stored.getConsumedAt()).isEqualTo(NOW);
        assertThat(stored.getReplacedBySelector()).isEqualTo(codec.selectorOf(rotated.rawToken()));
        assertThat(rotated.absoluteExpiresAt()).isEqualTo(Instant.parse("2026-09-13T02:00:00Z"));
        ArgumentCaptor<RefreshTokenSession> replacement = ArgumentCaptor.forClass(RefreshTokenSession.class);
        verify(repository).saveAndFlush(replacement.capture());
        assertThat(replacement.getValue().getFamilyId()).isEqualTo("family-1");
        assertThat(replacement.getValue().getAbsoluteExpiresAt()).isEqualTo(stored.getAbsoluteExpiresAt());
        assertThat(replacement.getValue().getSecretHash()).doesNotContain(rotated.rawToken());
    }

    @Test
    void reuse_of_consumed_token_revokes_entire_family() {
        RefreshTokenCodec codec = new RefreshTokenCodec(KEY);
        RefreshTokenCodec.IssuedToken original = codec.issue();
        RefreshTokenSession stored = session(original, enabledUser());
        stored.setConsumedAt(NOW.minusSeconds(1));
        when(repository.findBySelectorForUpdate(original.selector())).thenReturn(Optional.of(stored));
        when(repository.findFamilyForUpdate("family-1")).thenReturn(java.util.List.of(stored));

        assertThatThrownBy(() -> service(codec).rotate(original.rawToken()))
                .isInstanceOf(RefreshSessionException.class)
                .hasMessageContaining("REAUTHENTICATION_REQUIRED");
        assertThat(stored.getRevokedAt()).isEqualTo(NOW);
        verify(repository).saveAllAndFlush(java.util.List.of(stored));
        verify(repository, never()).save(any());
    }

    @Test
    void sequential_second_consume_locks_and_revokes_active_child_family() {
        RefreshTokenCodec codec = new RefreshTokenCodec(KEY);
        RefreshTokenCodec.IssuedToken original = codec.issue();
        RefreshTokenSession stored = session(original, enabledUser());
        java.util.List<RefreshTokenSession> persisted = new java.util.ArrayList<>();
        when(repository.findBySelectorForUpdate(original.selector())).thenReturn(Optional.of(stored));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            RefreshTokenSession child = invocation.getArgument(0);
            persisted.add(child);
            return child;
        });
        when(repository.findFamilyForUpdate("family-1")).thenAnswer(
                ignored -> java.util.stream.Stream.concat(
                                java.util.stream.Stream.of(stored), persisted.stream())
                        .toList());
        RefreshSessionService service = service(codec);

        service.rotate(original.rawToken());
        assertThatThrownBy(() -> service.rotate(original.rawToken()))
                .isInstanceOf(RefreshSessionException.class)
                .hasMessageContaining("REAUTHENTICATION_REQUIRED");

        assertThat(stored.getRevokedAt()).isEqualTo(NOW);
        assertThat(persisted).singleElement()
                .extracting(RefreshTokenSession::getRevokedAt)
                .isEqualTo(NOW);
        verify(repository).findFamilyForUpdate("family-1");
    }

    @Test
    void refresh_issues_access_token_while_holding_the_same_user_write_lock() {
        RefreshTokenCodec codec = new RefreshTokenCodec(KEY);
        RefreshTokenCodec.IssuedToken original = codec.issue();
        NguoiDung user = enabledUser();
        RefreshTokenSession stored = session(original, user);
        NguoiDungRepository users = org.mockito.Mockito.mock(NguoiDungRepository.class);
        JwtService jwt = org.mockito.Mockito.mock(JwtService.class);
        when(repository.findUserIdBySelector(original.selector()))
                .thenReturn(Optional.of(user.getMaNguoiDung()));
        when(users.findByIdForAuthWrite(user.getMaNguoiDung()))
                .thenReturn(Optional.of(user));
        when(repository.findBySelectorForUpdate(original.selector()))
                .thenReturn(Optional.of(stored));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwt.generateToken(user)).thenReturn("access-token");
        RefreshSessionService service = new RefreshSessionService(
                repository, codec, Clock.fixed(NOW, ZoneOffset.UTC), true, users, jwt);

        var authenticated = service.rotateAndIssueAccessToken(original.rawToken());

        assertThat(authenticated.accessToken()).isEqualTo("access-token");
        verify(users).findByIdForAuthWrite(user.getMaNguoiDung());
        verify(jwt).generateToken(user);
    }

    @Test
    void logout_locks_and_revokes_current_family() {
        RefreshTokenCodec codec = new RefreshTokenCodec(KEY);
        RefreshTokenCodec.IssuedToken token = codec.issue();
        RefreshTokenSession stored = session(token, enabledUser());
        when(repository.findBySelectorForUpdate(token.selector())).thenReturn(Optional.of(stored));
        when(repository.findFamilyForUpdate("family-1")).thenReturn(java.util.List.of(stored));

        service(codec).revokeCurrent(token.rawToken());

        assertThat(stored.getRevokedAt()).isEqualTo(NOW);
        verify(repository).saveAllAndFlush(java.util.List.of(stored));
    }

    @Test
    void wrong_secret_and_disabled_user_fail_closed() {
        RefreshTokenCodec codec = new RefreshTokenCodec(KEY);
        RefreshTokenCodec.IssuedToken original = codec.issue();
        RefreshTokenSession stored = session(original, enabledUser());
        when(repository.findBySelectorForUpdate(original.selector())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service(codec).rotate(original.selector() + ".wrong"))
                .isInstanceOf(RefreshSessionException.class);
        verify(repository, never()).save(any());

        stored.getNguoiDung().setDaKichHoat(false);
        when(repository.findFamilyForUpdate("family-1")).thenReturn(java.util.List.of(stored));
        assertThatThrownBy(() -> service(codec).rotate(original.rawToken()))
                .isInstanceOf(RefreshSessionException.class);
        assertThat(stored.getRevokedAt()).isEqualTo(NOW);
        verify(repository).saveAllAndFlush(java.util.List.of(stored));
    }

    private RefreshSessionService service(RefreshTokenCodec codec) {
        return new RefreshSessionService(repository, codec, Clock.fixed(NOW, ZoneOffset.UTC), true);
    }

    private RefreshTokenSession session(RefreshTokenCodec.IssuedToken token, NguoiDung user) {
        RefreshTokenSession session = new RefreshTokenSession();
        session.setSelector(token.selector());
        session.setSecretHash(token.secretHash());
        session.setFamilyId("family-1");
        session.setNguoiDung(user);
        session.setRememberMe(true);
        session.setIssuedAt(NOW.minusSeconds(60));
        session.setAbsoluteExpiresAt(Instant.parse("2026-09-13T02:00:00Z"));
        return session;
    }

    private NguoiDung enabledUser() {
        NguoiDung user = new NguoiDung();
        user.setMaNguoiDung(42);
        user.setTenDangNhap("alice");
        user.setDaKichHoat(true);
        return user;
    }
}
