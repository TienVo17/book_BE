package com.example.book_be.nguoidung.identity;

import com.example.book_be.nguoidung.domain.NguoiDung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SocialAuthServiceTest {
    private static final String REDIRECT = "https://tienvo17.vercel.app/tai-khoan/oauth/google/callback";
    private static final String ISSUER = "https://accounts.google.com";

    private OAuthTransactionService transactionService;
    private GoogleTokenExchange tokenExchange;
    private GoogleIdTokenVerifier verifier;
    private AuthIdentityService identityService;
    private SocialProviderProperties properties;
    private SocialAuthService service;

    @BeforeEach
    void setUp() {
        transactionService = mock(OAuthTransactionService.class);
        tokenExchange = mock(GoogleTokenExchange.class);
        verifier = mock(GoogleIdTokenVerifier.class);
        identityService = mock(AuthIdentityService.class);
        properties = new SocialProviderProperties(true, "client", "secret", REDIRECT);
        service = new SocialAuthService(transactionService, tokenExchange, verifier,
                mock(FacebookTokenExchange.class), mock(FacebookIdentityVerifier.class),
                identityService, properties);
    }

    private OAuthTransactionService.StartedFlow started() {
        return new OAuthTransactionService.StartedFlow(
                "state-1", "binding-1", "verifier-1", "challenge-1", "nonce-1");
    }

    private OAuthTransaction transaction() {
        OAuthTransaction transaction = new OAuthTransaction();
        transaction.setProvider("google");
        transaction.setFlowKind(OAuthFlowKind.LOGIN.name());
        transaction.setRedirectUri(REDIRECT);
        return transaction;
    }

    private ProviderIdentity identity() {
        return new ProviderIdentity("google", ISSUER, "sub-1", "reader@example.com", true, "Reader");
    }

    @Test
    void start_builds_a_google_authorization_url_with_pkce_and_state() {
        when(transactionService.start(eq("google"), eq(OAuthFlowKind.LOGIN), eq(REDIRECT), any()))
                .thenReturn(started());

        SocialAuthService.Authorization authorization = service.startLogin("google");

        assertThat(authorization.authorizationUrl())
                .startsWith("https://accounts.google.com/o/oauth2/v2/auth")
                .contains("client_id=client")
                .contains("response_type=code")
                .contains("code_challenge=challenge-1")
                .contains("code_challenge_method=S256")
                .contains("state=state-1")
                .contains("nonce=nonce-1");
        assertThat(authorization.browserBinding()).isEqualTo("binding-1");
    }

    /**
     * Requesting more than identity scopes would hand the application access to a user's mail
     * or drive that it has no reason to hold.
     */
    @Test
    void start_requests_only_identity_scopes() {
        when(transactionService.start(anyString(), any(), anyString(), any())).thenReturn(started());

        String url = service.startLogin("google").authorizationUrl();

        assertThat(url).contains("scope=openid+profile+email");
        assertThat(url).doesNotContain("drive", "gmail", "contacts");
    }

    @Test
    void callback_for_a_linked_identity_resolves_to_that_account() {
        NguoiDung linked = new NguoiDung();
        linked.setMaNguoiDung(7);
        when(transactionService.consume("state-1", "binding-1", "google")).thenReturn(transaction());
        when(transactionService.decryptVerifier(any())).thenReturn("verifier-1");
        when(tokenExchange.exchange("code-1", "verifier-1", REDIRECT))
                .thenReturn(Map.of("sub", "sub-1"));
        when(verifier.verify(any(), any())).thenReturn(identity());
        when(identityService.resolve(any()))
                .thenReturn(new AuthIdentityService.Resolution(linked, false, "reader@example.com"));

        SocialAuthService.CallbackResult result = service.completeCallback("google", "code-1", "state-1", "binding-1");

        assertThat(result.outcome()).isEqualTo(SocialAuthService.Outcome.AUTHENTICATED);
        assertThat(result.user()).isSameAs(linked);
    }

    @Test
    void callback_for_an_unknown_identity_requires_signup_and_creates_no_account() {
        when(transactionService.consume(anyString(), anyString(), anyString())).thenReturn(transaction());
        when(transactionService.decryptVerifier(any())).thenReturn("verifier-1");
        when(tokenExchange.exchange(anyString(), anyString(), anyString())).thenReturn(Map.of());
        when(verifier.verify(any(), any())).thenReturn(identity());
        when(identityService.resolve(any()))
                .thenReturn(new AuthIdentityService.Resolution(null, false, "new@example.com"));

        SocialAuthService.CallbackResult result = service.completeCallback("google", "code-1", "state-1", "binding-1");

        assertThat(result.outcome()).isEqualTo(SocialAuthService.Outcome.SIGNUP_REQUIRED);
        assertThat(result.user()).isNull();
    }

    @Test
    void callback_for_a_colliding_email_asks_for_proof_instead_of_linking() {
        when(transactionService.consume(anyString(), anyString(), anyString())).thenReturn(transaction());
        when(transactionService.decryptVerifier(any())).thenReturn("verifier-1");
        when(tokenExchange.exchange(anyString(), anyString(), anyString())).thenReturn(Map.of());
        when(verifier.verify(any(), any())).thenReturn(identity());
        when(identityService.resolve(any()))
                .thenReturn(new AuthIdentityService.Resolution(null, true, "reader@example.com"));

        SocialAuthService.CallbackResult result = service.completeCallback("google", "code-1", "state-1", "binding-1");

        assertThat(result.outcome()).isEqualTo(SocialAuthService.Outcome.LINK_REQUIRED);
        verify(identityService, never()).link(any(), any());
    }

    /**
     * A rejected state must stop before the authorization code is spent, otherwise a replayed
     * callback still burns a real code at the provider.
     */
    @Test
    void an_invalid_state_never_reaches_the_token_exchange() {
        when(transactionService.consume(anyString(), anyString(), anyString()))
                .thenThrow(new AuthIdentityException("OAUTH_STATE_INVALID"));

        assertThatThrownBy(() -> service.completeCallback("google", "code-1", "bad-state", "binding-1"))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("OAUTH_STATE_INVALID");

        verify(tokenExchange, never()).exchange(anyString(), anyString(), anyString());
    }

    @Test
    void the_nonce_from_the_stored_transaction_is_enforced_against_the_token() {
        OAuthTransaction transaction = transaction();
        when(transactionService.consume(anyString(), anyString(), anyString())).thenReturn(transaction);
        when(transactionService.decryptVerifier(any())).thenReturn("verifier-1");
        when(tokenExchange.exchange(anyString(), anyString(), anyString())).thenReturn(Map.of());
        when(verifier.verify(any(), any()))
                .thenThrow(new AuthIdentityException("OAUTH_TOKEN_INVALID"));

        assertThatThrownBy(() -> service.completeCallback("google", "code-1", "state-1", "binding-1"))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("OAUTH_TOKEN_INVALID");

        verify(identityService, never()).resolve(any());
    }

    @Test
    void a_disabled_provider_refuses_to_start_a_flow() {
        SocialAuthService disabled = new SocialAuthService(transactionService, tokenExchange,
                verifier, mock(FacebookTokenExchange.class), mock(FacebookIdentityVerifier.class),
                identityService, new SocialProviderProperties(false, "", "", ""));

        assertThatThrownBy(() -> disabled.startLogin("google"))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("PROVIDER_DISABLED");
    }
}
