package com.example.book_be.nguoidung.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ho so dang ky dang do song giua callback va buoc hoan tat. No la mot bearer secret trong
 * suot quang do, nen duoc kiem cung mot muc voi `oauth_transaction`.
 */
class SocialSignupIntentServiceTest {
    private static final String KEY = "0123456789abcdef0123456789abcdef";

    private InMemoryRepository repository;
    private MutableClock clock;
    private SocialSignupIntentService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        clock = new MutableClock(Instant.parse("2026-08-17T10:00:00Z"));
        service = new SocialSignupIntentService(repository, KEY, clock);
    }

    private ProviderIdentity facebookIdentity() {
        return new ProviderIdentity("facebook", "https://www.facebook.com", "fb-1", null, false, "Tien");
    }

    private ProviderIdentity googleIdentity() {
        return new ProviderIdentity("google", "https://accounts.google.com", "g-1",
                "nguoi@example.com", true, "Tien");
    }

    /** Ban luu tru chi duoc giu ban bam; lo database khong duoc phep du de hoan tat dang ky. */
    @Test
    void create_stores_only_a_hash_of_the_intent_token() {
        String token = service.create(facebookIdentity());

        assertThat(token).isNotBlank();
        assertThat(repository.saved).hasSize(1);
        assertThat(repository.saved.get(0).getIntentHash()).isNotEqualTo(token);
        assertThat(repository.saved.get(0).getProviderSubject()).isEqualTo("fb-1");
    }

    @Test
    void require_returns_the_intent_for_a_valid_token() {
        String token = service.create(googleIdentity());

        OAuthSignupIntent intent = service.require(token);

        assertThat(intent.getProvider()).isEqualTo("google");
        assertThat(intent.isEmailVerified()).isTrue();
        assertThat(intent.getEmail()).isEqualTo("nguoi@example.com");
    }

    @Test
    void require_rejects_an_unknown_expired_or_consumed_token() {
        assertThatThrownBy(() -> service.require("khong-ton-tai"))
                .isInstanceOf(AuthIdentityException.class);

        String expired = service.create(facebookIdentity());
        clock.advance(Duration.ofMinutes(31));
        assertThatThrownBy(() -> service.require(expired))
                .isInstanceOf(AuthIdentityException.class);

        clock.advance(Duration.ofMinutes(-31));
        String consumed = service.create(facebookIdentity());
        service.consume(consumed);
        assertThatThrownBy(() -> service.require(consumed))
                .isInstanceOf(AuthIdentityException.class);
    }

    /**
     * Facebook khong bao gio chung minh duoc email, nen dia chi phai do ung dung tu xac minh.
     * Ma chi duoc tra ve cho caller de gui di, khong bao gio luu nguyen van.
     */
    @Test
    void startEmailVerification_issues_a_code_and_stores_only_its_hash() {
        String token = service.create(facebookIdentity());

        String code = service.startEmailVerification(token, "nguoi@example.com");

        assertThat(code).matches("\\d{6}");
        OAuthSignupIntent intent = service.require(token);
        assertThat(intent.getEmail()).isEqualTo("nguoi@example.com");
        assertThat(intent.isEmailVerified()).isFalse();
        assertThat(intent.getEmailCodeHash()).isNotNull().isNotEqualTo(code);
    }

    @Test
    void confirmEmail_marks_the_address_verified_and_burns_the_code() {
        String token = service.create(facebookIdentity());
        String code = service.startEmailVerification(token, "nguoi@example.com");

        service.confirmEmail(token, code);

        OAuthSignupIntent intent = service.require(token);
        assertThat(intent.isEmailVerified()).isTrue();
        assertThat(intent.getEmailCodeHash()).isNull();
        // Ma da dung roi thi khong dung lai duoc.
        assertThatThrownBy(() -> service.confirmEmail(token, code))
                .isInstanceOf(AuthIdentityException.class);
    }

    @Test
    void confirmEmail_rejects_a_wrong_or_expired_code() {
        String token = service.create(facebookIdentity());
        service.startEmailVerification(token, "nguoi@example.com");

        assertThatThrownBy(() -> service.confirmEmail(token, "000000"))
                .isInstanceOf(AuthIdentityException.class);

        String fresh = service.create(facebookIdentity());
        String code = service.startEmailVerification(fresh, "nguoi@example.com");
        clock.advance(Duration.ofMinutes(11));
        assertThatThrownBy(() -> service.confirmEmail(fresh, code))
                .isInstanceOf(AuthIdentityException.class);
    }

    /** Ma sau chu so du ngan de doan het neu khong dem so lan sai. */
    @Test
    void confirmEmail_stops_accepting_after_too_many_wrong_attempts() {
        String token = service.create(facebookIdentity());
        String code = service.startEmailVerification(token, "nguoi@example.com");

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> service.confirmEmail(token, "000000"))
                    .isInstanceOf(AuthIdentityException.class);
        }

        assertThatThrownBy(() -> service.confirmEmail(token, code))
                .isInstanceOf(AuthIdentityException.class);
    }

    /** Doi dia chi phai huy bang chung cu, neu khong thi xac minh dia chi A roi dang ky bang B. */
    @Test
    void startEmailVerification_for_a_new_address_drops_the_previous_proof() {
        String token = service.create(facebookIdentity());
        String code = service.startEmailVerification(token, "nguoi@example.com");
        service.confirmEmail(token, code);

        service.startEmailVerification(token, "khac@example.com");

        OAuthSignupIntent intent = service.require(token);
        assertThat(intent.getEmail()).isEqualTo("khac@example.com");
        assertThat(intent.isEmailVerified()).isFalse();
    }

    /** Email cua Google da duoc provider xac minh, nen doi sang dia chi khac phai xac minh lai. */
    @Test
    void startEmailVerification_also_clears_a_provider_verified_address() {
        String token = service.create(googleIdentity());

        service.startEmailVerification(token, "khac@example.com");

        assertThat(service.require(token).isEmailVerified()).isFalse();
    }

    @Test
    void consume_marks_the_intent_used_exactly_once() {
        String token = service.create(googleIdentity());

        OAuthSignupIntent intent = service.consume(token);

        assertThat(intent.getConsumedAt()).isNotNull();
        assertThatThrownBy(() -> service.consume(token))
                .isInstanceOf(AuthIdentityException.class);
    }

    private static final class InMemoryRepository implements OAuthSignupIntentRepository {
        private final List<OAuthSignupIntent> saved = new ArrayList<>();

        @Override
        public Optional<OAuthSignupIntent> findByIntentHash(String intentHash) {
            return saved.stream()
                    .filter(intent -> intent.getIntentHash().equals(intentHash))
                    .findFirst();
        }

        @Override
        public <S extends OAuthSignupIntent> S save(S entity) {
            if (!saved.contains(entity)) {
                saved.add(entity);
            }
            return entity;
        }

        @Override
        public int deleteExpired(Instant cutoff) {
            return (int) saved.stream().filter(i -> i.getExpiresAt().isBefore(cutoff)).count();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
