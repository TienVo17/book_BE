package com.example.book_be.nguoidung.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthTransactionServiceTest {
    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private static final Instant START = Instant.parse("2026-08-15T10:00:00Z");

    private InMemoryTransactionRepository repository;
    private MutableClock clock;
    private OAuthTransactionService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryTransactionRepository();
        clock = new MutableClock(START);
        service = new OAuthTransactionService(repository, KEY, clock);
    }

    private OAuthTransactionService.StartedFlow start() {
        return service.start("google", OAuthFlowKind.LOGIN, "https://app.example/callback", null);
    }

    @Test
    void started_flow_returns_secrets_to_the_browser_but_stores_only_digests() {
        OAuthTransactionService.StartedFlow started = start();

        assertThat(started.state()).isNotBlank();
        assertThat(started.codeVerifier()).isNotBlank();
        assertThat(started.codeChallenge()).isNotBlank().isNotEqualTo(started.codeVerifier());

        OAuthTransaction stored = repository.only();
        assertThat(stored.getStateHash()).isNotEqualTo(started.state());
        assertThat(stored.getBrowserBindingHash()).isNotEqualTo(started.browserBinding());
        assertThat(new String(stored.getCodeVerifierEncrypted()))
                .doesNotContain(started.codeVerifier());
    }

    @Test
    void consuming_a_flow_returns_the_original_verifier() {
        OAuthTransactionService.StartedFlow started = start();

        OAuthTransaction consumed = service.consume(started.state(), started.browserBinding(), "google");

        assertThat(service.decryptVerifier(consumed)).isEqualTo(started.codeVerifier());
    }

    /**
     * An authorization code may be replayed by anyone who observes the callback URL. Only the
     * first callback may be honoured; the second must find the transaction already spent.
     */
    @Test
    void a_second_callback_for_the_same_state_is_rejected() {
        OAuthTransactionService.StartedFlow started = start();
        service.consume(started.state(), started.browserBinding(), "google");

        assertThatThrownBy(() -> service.consume(started.state(), started.browserBinding(), "google"))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("OAUTH_STATE_INVALID");
    }

    /**
     * Without binding the flow to the browser that started it, an attacker can send a victim a
     * prepared callback URL and have the victim's session adopt the attacker's provider account.
     */
    @Test
    void a_callback_from_a_different_browser_is_rejected() {
        OAuthTransactionService.StartedFlow started = start();

        assertThatThrownBy(() -> service.consume(started.state(), "some-other-browser", "google"))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("OAUTH_STATE_INVALID");
    }

    /**
     * A state minted for one provider must not be spendable at another provider's callback,
     * which is the classic mix-up attack.
     */
    @Test
    void a_state_minted_for_another_provider_is_rejected() {
        OAuthTransactionService.StartedFlow started = start();

        assertThatThrownBy(() -> service.consume(started.state(), started.browserBinding(), "facebook"))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("OAUTH_STATE_INVALID");
    }

    @Test
    void an_expired_flow_is_rejected() {
        OAuthTransactionService.StartedFlow started = start();
        clock.advance(Duration.ofMinutes(16));

        assertThatThrownBy(() -> service.consume(started.state(), started.browserBinding(), "google"))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("OAUTH_STATE_INVALID");
    }

    @Test
    void an_unknown_state_is_rejected() {
        assertThatThrownBy(() -> service.consume("never-issued", "browser", "google"))
                .isInstanceOf(AuthIdentityException.class)
                .hasMessageContaining("OAUTH_STATE_INVALID");
    }

    @Test
    void link_flows_remember_the_account_being_linked() {
        OAuthTransactionService.StartedFlow started =
                service.start("google", OAuthFlowKind.LINK, "https://app.example/callback", 42);

        OAuthTransaction consumed = service.consume(started.state(), started.browserBinding(), "google");

        assertThat(consumed.getFlowKind()).isEqualTo(OAuthFlowKind.LINK.name());
        assertThat(consumed.getMaNguoiDungMucTieu()).isEqualTo(42);
    }

    @Test
    void a_short_encryption_key_is_refused_at_construction() {
        assertThatThrownBy(() -> new OAuthTransactionService(repository, "too-short", clock))
                .isInstanceOf(IllegalStateException.class);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) { this.now = now; }
        void advance(Duration amount) { now = now.plus(amount); }
        @Override public Instant instant() { return now; }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }

    private static final class InMemoryTransactionRepository implements OAuthTransactionRepository {
        private final Map<String, OAuthTransaction> byStateHash = new HashMap<>();
        private long sequence;

        OAuthTransaction only() {
            assertThat(byStateHash).hasSize(1);
            return byStateHash.values().iterator().next();
        }

        @Override
        public Optional<OAuthTransaction> findByStateHash(String stateHash) {
            return Optional.ofNullable(byStateHash.get(stateHash));
        }

        @Override
        public <S extends OAuthTransaction> S save(S entity) {
            if (entity.getId() == null) {
                entity.setId(++sequence);
            }
            byStateHash.put(entity.getStateHash(), entity);
            return entity;
        }

        @Override
        public int deleteExpired(Instant cutoff) {
            return 0;
        }
    }
}
