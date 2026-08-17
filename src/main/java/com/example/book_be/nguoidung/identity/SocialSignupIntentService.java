package com.example.book_be.nguoidung.identity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

/**
 * Giu ho so dang ky dang do giua callback cua provider va buoc hoan tat.
 *
 * Callback khong duoc tao `nguoi_dung`: ai bo ngang o buoc dien ho so se de lai mot tai khoan
 * nua voi, khong dang nhap lai duoc, ma van chiem cho username/email. Ho so nam o day cho den
 * khi buoc hoan tat tao user + quyen + identity trong dung mot transaction.
 *
 * Token cua ho so la bearer secret trong suot quang do nay, nen chi luu ban bam - cung muc
 * bao ve voi `state` cua `oauth_transaction`.
 */
@Service
public class SocialSignupIntentService {
    private static final Duration LIFETIME = Duration.ofMinutes(30);
    private static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final int MINIMUM_KEY_BYTES = 32;

    private final OAuthSignupIntentRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] hashKey;
    private final Clock clock;

    @Autowired
    public SocialSignupIntentService(OAuthSignupIntentJpaRepository repository,
                                     @Value("${app.auth.oauth-encryption-key:}") String hashKey) {
        this.repository = repository;
        this.hashKey = hashKey == null ? new byte[0] : hashKey.getBytes(StandardCharsets.UTF_8);
        this.clock = Clock.systemUTC();
    }

    SocialSignupIntentService(OAuthSignupIntentRepository repository, String hashKey, Clock clock) {
        this.repository = repository;
        this.hashKey = hashKey == null ? new byte[0] : hashKey.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    /**
     * @return token tho de tra ve trinh duyet; ban luu tru chi giu ban bam
     */
    @Transactional
    public String create(ProviderIdentity identity) {
        requireConfigured();
        String token = randomUrlSafe(32);
        Instant now = clock.instant();

        OAuthSignupIntent intent = new OAuthSignupIntent();
        intent.setIntentHash(hash(token));
        intent.setProvider(identity.provider());
        intent.setIssuer(identity.issuer());
        intent.setProviderSubject(identity.subject());
        // Chi giu dia chi khi chinh provider khang dinh da xac minh. Mot dia chi chua xac
        // minh khong chung minh duoc nguoi dang dang nhap kiem soat hom thu do.
        intent.setEmail(identity.trustedEmail());
        intent.setEmailVerified(identity.trustedEmail() != null);
        intent.setTenHienThi(identity.displayName());
        intent.setCreatedAt(now);
        intent.setExpiresAt(now.plus(LIFETIME));
        repository.save(intent);
        return token;
    }

    /**
     * Moi ly do that bai deu tra ve cung mot ma loi: phan biet "khong ton tai" voi "da het
     * han" se cho ke tan cong biet minh doan dung o cho nao.
     */
    @Transactional(readOnly = true)
    public OAuthSignupIntent require(String token) {
        requireConfigured();
        if (token == null || token.isBlank()) {
            throw invalid();
        }
        OAuthSignupIntent intent = repository.findByIntentHash(hash(token)).orElseThrow(this::invalid);
        if (intent.getConsumedAt() != null || !clock.instant().isBefore(intent.getExpiresAt())) {
            throw invalid();
        }
        return intent;
    }

    /**
     * Bat dau xac minh mot dia chi email do nguoi dung chon.
     *
     * @return ma tho de caller gui di; khong bao gio duoc ghi vao log hay tra ve trinh duyet
     */
    @Transactional
    public String startEmailVerification(String token, String email) {
        OAuthSignupIntent intent = require(token);
        String normalized = normalizeEmail(email);
        if (normalized == null) {
            throw new AuthIdentityException("EMAIL_REQUIRED");
        }
        String code = randomDigits();
        Instant now = clock.instant();

        intent.setEmail(normalized);
        // Doi dia chi phai huy moi bang chung truoc do, ke ca bang chung tu provider: neu
        // khong thi xac minh dia chi A xong doi sang B la dang ky duoc bang dia chi nguoi la.
        intent.setEmailVerified(false);
        intent.setEmailCodeHash(hash(code));
        intent.setEmailCodeExpiresAt(now.plus(CODE_LIFETIME));
        intent.setEmailCodeAttempts(0);
        repository.save(intent);
        return code;
    }

    @Transactional
    public void confirmEmail(String token, String code) {
        OAuthSignupIntent intent = require(token);
        if (intent.getEmailCodeHash() == null
                || intent.getEmailCodeExpiresAt() == null
                || !clock.instant().isBefore(intent.getEmailCodeExpiresAt())
                || intent.getEmailCodeAttempts() >= MAX_CODE_ATTEMPTS) {
            throw new AuthIdentityException("EMAIL_CODE_INVALID");
        }
        // Dem truoc khi so sanh: thoat som khi dung ma con lam tang bo dem chi khi sai thi
        // mot lan chay bi ngat giua chung se tra lai lan doan mien phi.
        intent.setEmailCodeAttempts(intent.getEmailCodeAttempts() + 1);
        if (code == null || !constantTimeEquals(intent.getEmailCodeHash(), hash(code))) {
            repository.save(intent);
            throw new AuthIdentityException("EMAIL_CODE_INVALID");
        }

        intent.setEmailVerified(true);
        // Ma dung mot lan. De lai thi phat lai chinh no la vao duoc.
        intent.setEmailCodeHash(null);
        intent.setEmailCodeExpiresAt(null);
        repository.save(intent);
    }

    @Transactional
    public OAuthSignupIntent consume(String token) {
        OAuthSignupIntent intent = require(token);
        intent.setConsumedAt(clock.instant());
        return repository.save(intent);
    }

    @Transactional
    public int purgeExpired() {
        return repository.deleteExpired(clock.instant());
    }

    /** Email so sanh khong phan biet hoa thuong, khop voi cach tai khoan mat khau doi chieu. */
    static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(hashKey);
            byte[] result = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(result);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }

    private String randomUrlSafe(int byteCount) {
        byte[] bytes = new byte[byteCount];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String randomDigits() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private void requireConfigured() {
        if (hashKey.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException("Signup intent service is not configured");
        }
    }

    private AuthIdentityException invalid() {
        return new AuthIdentityException("SIGNUP_INTENT_INVALID");
    }
}
