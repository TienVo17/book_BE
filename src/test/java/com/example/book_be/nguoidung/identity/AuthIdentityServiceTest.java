package com.example.book_be.nguoidung.identity;

import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthIdentityServiceTest {
    private AuthIdentityRepository identityRepository;
    private NguoiDungRepository userRepository;
    private AuthIdentityService service;

    private static final String ISSUER = "https://accounts.google.com";

    @BeforeEach
    void setUp() {
        identityRepository = mock(AuthIdentityRepository.class);
        userRepository = mock(NguoiDungRepository.class);
        service = new AuthIdentityService(identityRepository, userRepository);
    }

    private NguoiDung user(int id, String email) {
        NguoiDung user = new NguoiDung();
        user.setMaNguoiDung(id);
        user.setEmail(email);
        user.setTenDangNhap("reader" + id);
        user.setDaKichHoat(true);
        return user;
    }

    private ProviderIdentity claims(String subject, String email, boolean emailVerified) {
        return new ProviderIdentity("google", ISSUER, subject, email, emailVerified, "Reader");
    }

    @Test
    void existing_identity_resolves_to_its_linked_account() {
        NguoiDung linked = user(7, "reader@example.com");
        AuthIdentity identity = new AuthIdentity();
        identity.setNguoiDung(linked);
        when(identityRepository.findByProviderAndIssuerAndProviderSubject("google", ISSUER, "sub-1"))
                .thenReturn(Optional.of(identity));

        AuthIdentityService.Resolution resolution =
                service.resolve(claims("sub-1", "reader@example.com", true));

        assertThat(resolution.linkedUser()).isSameAs(linked);
        assertThat(resolution.requiresSignup()).isFalse();
    }

    /**
     * Auto-linking on a matching email lets anyone who can obtain a provider account bearing
     * a victim's address take over that victim's orders and addresses. Proof of control over
     * the existing password account is the only thing that may link it.
     */
    @Test
    void email_collision_never_auto_links_an_existing_password_account() {
        when(identityRepository.findByProviderAndIssuerAndProviderSubject(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("reader@example.com")).thenReturn(user(7, "reader@example.com"));

        AuthIdentityService.Resolution resolution =
                service.resolve(claims("sub-new", "reader@example.com", true));

        assertThat(resolution.linkedUser()).isNull();
        assertThat(resolution.collidesWithExistingAccount()).isTrue();
        verify(identityRepository, never()).save(any());
    }

    @Test
    void unverified_provider_email_is_not_trusted_for_collision_or_signup() {
        when(identityRepository.findByProviderAndIssuerAndProviderSubject(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        AuthIdentityService.Resolution resolution =
                service.resolve(claims("sub-new", "reader@example.com", false));

        assertThat(resolution.trustedEmail()).isNull();
        assertThat(resolution.requiresSignup()).isTrue();
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void unknown_identity_without_collision_requires_completing_signup() {
        when(identityRepository.findByProviderAndIssuerAndProviderSubject(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@example.com")).thenReturn(null);

        AuthIdentityService.Resolution resolution =
                service.resolve(claims("sub-new", "new@example.com", true));

        assertThat(resolution.requiresSignup()).isTrue();
        assertThat(resolution.collidesWithExistingAccount()).isFalse();
        assertThat(resolution.trustedEmail()).isEqualTo("new@example.com");
    }

    @Test
    void linking_an_identity_already_owned_by_another_account_fails_closed() {
        NguoiDung owner = user(7, "owner@example.com");
        NguoiDung requester = user(9, "other@example.com");
        AuthIdentity existing = new AuthIdentity();
        existing.setNguoiDung(owner);
        when(identityRepository.findByProviderAndIssuerAndProviderSubject("google", ISSUER, "sub-1"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.link(requester, claims("sub-1", "owner@example.com", true)))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("IDENTITY_ALREADY_LINKED");

        verify(identityRepository, never()).save(any());
    }

    @Test
    void relinking_the_same_identity_to_the_same_account_is_idempotent() {
        NguoiDung owner = user(7, "owner@example.com");
        AuthIdentity existing = new AuthIdentity();
        existing.setNguoiDung(owner);
        when(identityRepository.findByProviderAndIssuerAndProviderSubject("google", ISSUER, "sub-1"))
                .thenReturn(Optional.of(existing));

        assertThat(service.link(owner, claims("sub-1", "owner@example.com", true))).isSameAs(existing);
        verify(identityRepository, never()).save(any());
    }
}
