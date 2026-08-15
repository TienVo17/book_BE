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

class FacebookAuthFlowTest {
    private static final String REDIRECT =
            "https://tienvo17.vercel.app/tai-khoan/oauth/facebook/callback";

    private OAuthTransactionService transactionService;
    private FacebookTokenExchange tokenExchange;
    private FacebookIdentityVerifier verifier;
    private AuthIdentityService identityService;
    private SocialAuthService service;

    @BeforeEach
    void setUp() {
        transactionService = mock(OAuthTransactionService.class);
        tokenExchange = mock(FacebookTokenExchange.class);
        verifier = mock(FacebookIdentityVerifier.class);
        identityService = mock(AuthIdentityService.class);
        service = new SocialAuthService(transactionService,
                mock(GoogleTokenExchange.class), mock(GoogleIdTokenVerifier.class),
                tokenExchange, verifier,
                identityService,
                new SocialProviderProperties(false, "", "", "",
                        true, "app-id", "app-secret", REDIRECT));
    }

    private OAuthTransactionService.StartedFlow started() {
        return new OAuthTransactionService.StartedFlow(
                "state-1", "binding-1", "verifier-1", "challenge-1", "nonce-1");
    }

    private OAuthTransaction transaction() {
        OAuthTransaction transaction = new OAuthTransaction();
        transaction.setProvider("facebook");
        transaction.setFlowKind(OAuthFlowKind.LOGIN.name());
        transaction.setRedirectUri(REDIRECT);
        return transaction;
    }

    private ProviderIdentity identity() {
        return new ProviderIdentity("facebook", FacebookIdentityVerifier.ISSUER,
                "fb-1", "reader@example.com", false, "Reader");
    }

    @Test
    void start_builds_a_facebook_authorization_url_with_pkce_and_state() {
        when(transactionService.start(eq("facebook"), eq(OAuthFlowKind.LOGIN), eq(REDIRECT), any()))
                .thenReturn(started());

        SocialAuthService.Authorization authorization = service.startLogin("facebook");

        assertThat(authorization.authorizationUrl())
                .startsWith("https://www.facebook.com/")
                .contains("/dialog/oauth")
                .contains("client_id=app-id")
                .contains("response_type=code")
                .contains("code_challenge=challenge-1")
                .contains("code_challenge_method=S256")
                .contains("state=state-1");
        assertThat(authorization.browserBinding()).isEqualTo("binding-1");
    }

    /**
     * Requesting more than public_profile and email would pull in Graph data the application
     * has no reason to hold, and would drag the app into a heavier App Review.
     */
    @Test
    void start_requests_only_public_profile_and_email() {
        when(transactionService.start(anyString(), any(), anyString(), any())).thenReturn(started());

        String url = service.startLogin("facebook").authorizationUrl();

        assertThat(url).contains("scope=public_profile%2Cemail");
        assertThat(url).doesNotContain("user_friends", "publish", "pages");
    }

    @Test
    void callback_for_a_linked_identity_resolves_to_that_account() {
        NguoiDung linked = new NguoiDung();
        linked.setMaNguoiDung(9);
        when(transactionService.consume("state-1", "binding-1", "facebook")).thenReturn(transaction());
        when(transactionService.decryptVerifier(any())).thenReturn("verifier-1");
        when(tokenExchange.exchange("code-1", "verifier-1", REDIRECT))
                .thenReturn(new FacebookTokenExchange.ExchangeResult(Map.of("id", "fb-1"), Map.of()));
        when(verifier.verify(any(), any())).thenReturn(identity());
        when(identityService.resolve(any()))
                .thenReturn(new AuthIdentityService.Resolution(linked, false, null));

        SocialAuthService.CallbackResult result =
                service.completeCallback("facebook", "code-1", "state-1", "binding-1");

        assertThat(result.outcome()).isEqualTo(SocialAuthService.Outcome.AUTHENTICATED);
        assertThat(result.user()).isSameAs(linked);
    }

    /**
     * Facebook never tells us an address is verified, so a new Facebook identity can only ever
     * lead to signup with app-side verification, never to an automatic link by email.
     */
    @Test
    void an_unverified_facebook_email_leads_to_signup_rather_than_linking() {
        when(transactionService.consume(anyString(), anyString(), anyString())).thenReturn(transaction());
        when(transactionService.decryptVerifier(any())).thenReturn("verifier-1");
        when(tokenExchange.exchange(anyString(), anyString(), anyString()))
                .thenReturn(new FacebookTokenExchange.ExchangeResult(Map.of(), Map.of()));
        when(verifier.verify(any(), any())).thenReturn(identity());
        when(identityService.resolve(any()))
                .thenReturn(new AuthIdentityService.Resolution(null, false, null));

        SocialAuthService.CallbackResult result =
                service.completeCallback("facebook", "code-1", "state-1", "binding-1");

        assertThat(result.outcome()).isEqualTo(SocialAuthService.Outcome.SIGNUP_REQUIRED);
        verify(identityService, never()).link(any(), any());
    }

    @Test
    void an_invalid_state_never_reaches_the_token_exchange() {
        when(transactionService.consume(anyString(), anyString(), anyString()))
                .thenThrow(new AuthIdentityException("OAUTH_STATE_INVALID"));

        assertThatThrownBy(() -> service.completeCallback("facebook", "code-1", "bad", "binding-1"))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("OAUTH_STATE_INVALID");

        verify(tokenExchange, never()).exchange(anyString(), anyString(), anyString());
    }

    @Test
    void a_disabled_facebook_refuses_to_start_a_flow() {
        SocialAuthService disabled = new SocialAuthService(transactionService,
                mock(GoogleTokenExchange.class), mock(GoogleIdTokenVerifier.class),
                tokenExchange, verifier, identityService,
                new SocialProviderProperties(false, "", "", "", false, "", "", ""));

        assertThatThrownBy(() -> disabled.startLogin("facebook"))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("PROVIDER_DISABLED");
    }

    /** An unknown provider name must never fall through to a live flow. */
    @Test
    void an_unknown_provider_is_refused() {
        assertThatThrownBy(() -> service.startLogin("tiktok"))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("PROVIDER_DISABLED");
    }
}
