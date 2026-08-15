package com.example.book_be.nguoidung.identity;

import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.domain.Quyen;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.repository.QuyenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SocialSignupServiceTest {
    private NguoiDungRepository userRepository;
    private QuyenRepository quyenRepository;
    private AuthIdentityService identityService;
    private SocialSignupService service;

    private static final String ISSUER = "https://accounts.google.com";

    @BeforeEach
    void setUp() {
        userRepository = mock(NguoiDungRepository.class);
        quyenRepository = mock(QuyenRepository.class);
        identityService = mock(AuthIdentityService.class);
        service = new SocialSignupService(userRepository, quyenRepository, identityService);

        Quyen userRole = new Quyen();
        userRole.setTenQuyen("USER");
        when(quyenRepository.findByTenQuyen("USER")).thenReturn(userRole);
        when(userRepository.save(any(NguoiDung.class))).thenAnswer(call -> call.getArgument(0));
        when(identityService.resolve(any()))
                .thenReturn(new AuthIdentityService.Resolution(null, false, "new@example.com"));
    }

    private ProviderIdentity claims(String subject, String email) {
        return new ProviderIdentity("google", ISSUER, subject, email, true, "Reader");
    }

    private SocialSignupService.CompletionRequest request(String username, String email) {
        return new SocialSignupService.CompletionRequest(username, email, "Doc", "Gia");
    }

    @Test
    void completion_creates_user_role_and_identity_together() {
        when(userRepository.existsByTenDangNhap("reader")).thenReturn(false);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);

        NguoiDung created = service.complete(claims("sub-1", "new@example.com"),
                request("reader", "new@example.com"));

        assertThat(created.getTenDangNhap()).isEqualTo("reader");
        assertThat(created.getDaKichHoat()).isTrue();
        assertThat(created.getDanhSachQuyen()).extracting(Quyen::getTenQuyen).containsExactly("USER");
        verify(identityService).link(created, claims("sub-1", "new@example.com"));
    }

    /**
     * A social-only account has no password to verify. Storing a random or empty hash would
     * make the password login path a coin flip on bcrypt behaviour; null keeps that path
     * unambiguously closed.
     */
    @Test
    void social_only_account_gets_no_usable_password() {
        when(userRepository.existsByTenDangNhap(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        NguoiDung created = service.complete(claims("sub-1", "new@example.com"),
                request("reader", "new@example.com"));

        assertThat(created.getMatKhau()).isNull();
    }

    @Test
    void taken_username_is_rejected_before_any_identity_is_linked() {
        when(userRepository.existsByTenDangNhap("reader")).thenReturn(true);

        assertThatThrownBy(() -> service.complete(claims("sub-1", "new@example.com"),
                request("reader", "new@example.com")))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("USERNAME_TAKEN");

        verify(userRepository, never()).save(any());
        verify(identityService, never()).link(any(), any());
    }

    /**
     * The provider only proves control of the address it verified. Letting the completion
     * form submit a different address would let anyone claim a stranger's email.
     */
    @Test
    void completion_email_must_match_the_verified_provider_email() {
        when(userRepository.existsByTenDangNhap(anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.complete(claims("sub-1", "verified@example.com"),
                request("reader", "someone-else@example.com")))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("EMAIL_NOT_VERIFIED");

        verify(userRepository, never()).save(any());
    }

    /**
     * Two callbacks racing on the same provider account must not create two users. The unique
     * index is the authority; losing the race has to roll the whole thing back, not continue.
     */
    @Test
    void losing_a_unique_index_race_surfaces_as_a_retryable_conflict() {
        when(userRepository.existsByTenDangNhap(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(identityService.link(any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.complete(claims("sub-1", "new@example.com"),
                request("reader", "new@example.com")))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("IDENTITY_RACE");
    }

    @Test
    void an_already_linked_provider_account_cannot_sign_up_again() {
        when(identityService.resolve(any())).thenReturn(
                new AuthIdentityService.Resolution(new NguoiDung(), false, "new@example.com"));
        when(userRepository.existsByTenDangNhap(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.complete(claims("sub-1", "new@example.com"),
                request("reader", "new@example.com")))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("IDENTITY_ALREADY_LINKED");

        verify(userRepository, never()).save(any());
    }

    @Test
    void missing_user_role_fails_closed_instead_of_creating_a_privilegeless_account() {
        when(userRepository.existsByTenDangNhap(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(quyenRepository.findByTenQuyen("USER")).thenReturn(null);

        assertThatThrownBy(() -> service.complete(claims("sub-1", "new@example.com"),
                request("reader", "new@example.com")))
                .isInstanceOf(AuthIdentityException.class);

        verify(userRepository, never()).save(any());
    }
}
