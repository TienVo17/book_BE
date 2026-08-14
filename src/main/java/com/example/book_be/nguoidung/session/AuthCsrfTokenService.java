package com.example.book_be.nguoidung.session;

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
public class AuthCsrfTokenService {
    public static final String COOKIE_NAME = "__Host-csrf";
    private static final int MINIMUM_KEY_BYTES = 32;
    private static final String ALGORITHM = "HmacSHA256";

    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] key;

    public AuthCsrfTokenService(@Value("${app.auth.csrf-hmac-key:}") String key,
                                @Value("${app.auth.refresh-enabled:false}") boolean refreshEnabled) {
        byte[] bytes = key == null ? new byte[0] : key.getBytes(StandardCharsets.UTF_8);
        if (refreshEnabled && bytes.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException(
                    "AUTH_CSRF_HMAC_KEY must contain at least 32 bytes when refresh sessions are enabled");
        }
        this.key = bytes;
    }

    public String issueToken() {
        requireConfigured();
        byte[] nonce = new byte[24];
        secureRandom.nextBytes(nonce);
        String encodedNonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        return encodedNonce + "." + sign(encodedNonce);
    }

    public boolean isValid(String token) {
        requireConfigured();
        if (token == null) {
            return false;
        }
        int separator = token.indexOf('.');
        if (separator <= 0 || separator != token.lastIndexOf('.') || separator == token.length() - 1) {
            return false;
        }
        String nonce = token.substring(0, separator);
        String signature = token.substring(separator + 1);
        return MessageDigest.isEqual(sign(nonce).getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII));
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    private void requireConfigured() {
        if (key.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException("CSRF token service is not configured");
        }
    }
}
