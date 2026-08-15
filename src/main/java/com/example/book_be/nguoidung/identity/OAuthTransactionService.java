package com.example.book_be.nguoidung.identity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Giu trang thai mot lan cua mot luot dang nhap qua provider.
 *
 * Ung dung chay STATELESS va co the co nhieu instance, nen khong dung HttpSession: mot luot
 * bat dau o instance nay phai duoc callback o instance khac xac minh duoc. Database la noi
 * duy nhat giu trang thai do.
 *
 * `state` va `browser binding` chi duoc luu duoi dang bam, con `code_verifier` phai lay lai
 * duoc gia tri goc de gui cho provider nen duoc ma hoa AES-GCM.
 */
@Service
public class OAuthTransactionService {
    private static final Duration LIFETIME = Duration.ofMinutes(15);
    private static final int MINIMUM_KEY_BYTES = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final OAuthTransactionRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] encryptionKey;
    private final Clock clock;

    @Autowired
    public OAuthTransactionService(OAuthTransactionJpaRepository repository,
                                   @Value("${app.auth.oauth-encryption-key:}") String encryptionKey,
                                   @Value("${app.auth.google-enabled:false}") boolean googleEnabled,
                                   @Value("${app.auth.facebook-enabled:false}") boolean facebookEnabled) {
        this.repository = repository;
        byte[] key = encryptionKey == null ? new byte[0] : encryptionKey.getBytes(StandardCharsets.UTF_8);
        // Chi bat buoc khi thuc su co provider duoc bat. Tat ca provider tat thi ung dung
        // van khoi dong duoc ma khong can cau hinh khoa.
        if ((googleEnabled || facebookEnabled) && key.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException(
                    "AUTH_OAUTH_ENCRYPTION_KEY must contain at least 32 bytes when a social provider is enabled");
        }
        this.encryptionKey = key;
        this.clock = Clock.systemUTC();
    }

    OAuthTransactionService(OAuthTransactionRepository repository, String encryptionKey, Clock clock) {
        this.repository = repository;
        byte[] key = encryptionKey == null ? new byte[0] : encryptionKey.getBytes(StandardCharsets.UTF_8);
        if (key.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException("OAuth encryption key must contain at least 32 bytes");
        }
        this.encryptionKey = key;
        this.clock = clock;
    }

    @Transactional
    public StartedFlow start(String provider, OAuthFlowKind flowKind, String redirectUri,
                             Integer targetUserId) {
        requireConfigured();
        String state = randomUrlSafe(32);
        String browserBinding = randomUrlSafe(32);
        String codeVerifier = randomUrlSafe(64);
        String nonce = randomUrlSafe(24);
        Instant now = clock.instant();

        OAuthTransaction transaction = new OAuthTransaction();
        transaction.setStateHash(hash(state));
        transaction.setProvider(provider);
        transaction.setFlowKind(flowKind.name());
        transaction.setBrowserBindingHash(hash(browserBinding));
        transaction.setNonceHash(hash(nonce));
        transaction.setCodeVerifierEncrypted(encrypt(codeVerifier));
        transaction.setRedirectUri(redirectUri);
        transaction.setMaNguoiDungMucTieu(targetUserId);
        transaction.setCreatedAt(now);
        transaction.setExpiresAt(now.plus(LIFETIME));
        repository.save(transaction);

        return new StartedFlow(state, browserBinding, codeVerifier, deriveChallenge(codeVerifier), nonce);
    }

    /**
     * Xac minh va tieu mot luot dang nhap. Moi ly do that bai deu tra ve cung mot ma loi:
     * phan biet "state khong ton tai" voi "state cua trinh duyet khac" se cho ke tan cong
     * biet minh doan dung o cho nao.
     */
    @Transactional
    public OAuthTransaction consume(String state, String browserBinding, String expectedProvider) {
        requireConfigured();
        if (state == null || browserBinding == null) {
            throw invalidState();
        }
        Optional<OAuthTransaction> found = repository.findByStateHash(hash(state));
        OAuthTransaction transaction = found.orElseThrow(this::invalidState);

        Instant now = clock.instant();
        if (transaction.getConsumedAt() != null
                || !now.isBefore(transaction.getExpiresAt())
                || !constantTimeEquals(transaction.getBrowserBindingHash(), hash(browserBinding))
                || !transaction.getProvider().equals(expectedProvider)) {
            throw invalidState();
        }

        transaction.setConsumedAt(now);
        return repository.save(transaction);
    }

    public String decryptVerifier(OAuthTransaction transaction) {
        requireConfigured();
        return decrypt(transaction.getCodeVerifierEncrypted());
    }

    @Transactional
    public int purgeExpired() {
        return repository.deleteExpired(clock.instant());
    }

    /**
     * PKCE S256: provider chi thay challenge luc bat dau, con verifier chi duoc gui o buoc
     * doi code. Ke bat duoc authorization code ma khong co verifier thi khong doi duoc token.
     */
    private String deriveChallenge(String codeVerifier) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String hash(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update(encryptionKey);
            byte[] result = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(result);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private byte[] encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return combined;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("AES-GCM is unavailable", exception);
        }
    }

    private String decrypt(byte[] combined) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(combined, GCM_IV_BYTES, combined.length - GCM_IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new AuthIdentityException("OAUTH_STATE_INVALID");
        }
    }

    private SecretKeySpec aesKey() {
        byte[] key = new byte[32];
        System.arraycopy(encryptionKey, 0, key, 0, Math.min(encryptionKey.length, 32));
        return new SecretKeySpec(key, "AES");
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

    private void requireConfigured() {
        if (encryptionKey.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException("OAuth transaction service is not configured");
        }
    }

    private AuthIdentityException invalidState() {
        return new AuthIdentityException("OAUTH_STATE_INVALID");
    }

    /**
     * Cac gia tri chi ton tai trong bo nho va tra ve trinh duyet; ban luu tru chi giu dang
     * bam hoac ma hoa.
     */
    public record StartedFlow(String state, String browserBinding, String codeVerifier,
                              String codeChallenge, String nonce) {
    }
}
