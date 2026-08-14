package com.example.book_be.nguoidung.session;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class RefreshTokenCodec {
    private static final int SELECTOR_BYTES = 18;
    private static final int SECRET_BYTES = 32;
    private static final int MINIMUM_KEY_BYTES = 32;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] hmacKey;

    public RefreshTokenCodec(String hmacKey) {
        this(hmacKey, true, true);
    }

    @Autowired
    public RefreshTokenCodec(
            @Value("${app.auth.refresh-hmac-key:}") String hmacKey,
            @Value("${app.auth.refresh-enabled:false}") boolean refreshEnabled) {
        this(hmacKey, refreshEnabled, true);
    }

    private RefreshTokenCodec(String hmacKey, boolean refreshEnabled, boolean ignored) {
        byte[] key = hmacKey == null ? new byte[0] : hmacKey.getBytes(StandardCharsets.UTF_8);
        if (refreshEnabled && key.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException(
                    "AUTH_REFRESH_HMAC_KEY must contain at least 32 bytes when refresh sessions are enabled");
        }
        this.hmacKey = key;
    }

    public IssuedToken issue() {
        requireConfigured();
        String selector = randomUrlSafe(SELECTOR_BYTES);
        String secret = randomUrlSafe(SECRET_BYTES);
        return new IssuedToken(selector + "." + secret, selector, hashSecret(secret));
    }

    public String selectorOf(String rawToken) {
        TokenParts parts = parse(rawToken);
        return parts == null ? null : parts.selector();
    }

    public boolean matches(String rawToken, String expectedSelector, String expectedSecretHash) {
        requireConfigured();
        TokenParts parts = parse(rawToken);
        if (parts == null || expectedSelector == null || expectedSecretHash == null
                || !MessageDigest.isEqual(parts.selector().getBytes(StandardCharsets.US_ASCII),
                expectedSelector.getBytes(StandardCharsets.US_ASCII))) {
            return false;
        }
        byte[] actual = hashSecret(parts.secret()).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = expectedSecretHash.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actual, expected);
    }

    private TokenParts parse(String rawToken) {
        if (rawToken == null) {
            return null;
        }
        int separator = rawToken.indexOf('.');
        if (separator <= 0 || separator != rawToken.lastIndexOf('.') || separator == rawToken.length() - 1) {
            return null;
        }
        String selector = rawToken.substring(0, separator);
        String secret = rawToken.substring(separator + 1);
        if (!selector.matches("[A-Za-z0-9_-]+") || !secret.matches("[A-Za-z0-9_-]+")) {
            return null;
        }
        return new TokenParts(selector, secret);
    }

    private String hashSecret(String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(hmacKey, HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(secret.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    private String randomUrlSafe(int byteCount) {
        byte[] bytes = new byte[byteCount];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void requireConfigured() {
        if (hmacKey.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException("Refresh token codec is not configured");
        }
    }

    public record IssuedToken(String rawToken, String selector, String secretHash) {
    }

    private record TokenParts(String selector, String secret) {
    }
}
