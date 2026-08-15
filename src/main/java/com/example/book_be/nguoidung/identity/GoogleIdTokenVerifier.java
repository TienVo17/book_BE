package com.example.book_be.nguoidung.identity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Kiem tra cac claim cua ID token Google sau khi chu ky da duoc Spring Security xac minh
 * bang JWK cua Google.
 *
 * Chu ky hop le moi chi chung minh "Google phat ra token nay", chua chung minh "token nay
 * danh cho ung dung nay va cho dung luot dang nhap dang cho". Bon kiem tra duoi day moi lam
 * not phan con lai.
 */
@Component
public class GoogleIdTokenVerifier {
    /** Google phat ca hai dang issuer nay va deu hop le. */
    private static final Set<String> ISSUERS =
            Set.of("https://accounts.google.com", "accounts.google.com");

    private final String clientId;
    private final Clock clock;

    @Autowired
    public GoogleIdTokenVerifier(@Value("${app.auth.google-client-id:}") String clientId) {
        this(clientId, Clock.systemUTC());
    }

    GoogleIdTokenVerifier(String clientId, Clock clock) {
        this.clientId = clientId;
        this.clock = clock;
    }

    public ProviderIdentity verify(Map<String, Object> claims, String expectedNonce) {
        String issuer = string(claims.get("iss"));
        if (issuer == null || !ISSUERS.contains(issuer)) {
            throw invalidToken();
        }
        // Token cua mot client Google khac van la token that. Khong kiem audience thi bat ky
        // ung dung Google nao cung dang nhap duoc vao day.
        if (!constantTimeEquals(string(claims.get("aud")), clientId)) {
            throw invalidToken();
        }
        String subject = string(claims.get("sub"));
        if (subject == null || subject.isBlank()) {
            throw invalidToken();
        }
        Instant expiry = instant(claims.get("exp"));
        if (expiry == null || !clock.instant().isBefore(expiry)) {
            throw invalidToken();
        }
        // Nonce buoc token nay vao dung luot dang nhap ma trinh duyet nay da bat dau.
        if (expectedNonce != null && !constantTimeEquals(string(claims.get("nonce")), expectedNonce)) {
            throw invalidToken();
        }

        return new ProviderIdentity(
                "google",
                issuer,
                subject,
                string(claims.get("email")),
                isTrue(claims.get("email_verified")),
                string(claims.get("name")));
    }

    /**
     * Google tra email_verified khi la boolean, khi la chuoi tuy endpoint. Chi dung mot phep
     * ep kieu se lam chuoi "false" thanh true.
     */
    private boolean isTrue(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value instanceof String text && "true".equalsIgnoreCase(text);
    }

    private String string(Object value) {
        return value instanceof String text ? text : null;
    }

    private Instant instant(Object value) {
        if (value instanceof Number number) {
            return Instant.ofEpochSecond(number.longValue());
        }
        return null;
    }

    private boolean constantTimeEquals(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private AuthIdentityException invalidToken() {
        return new AuthIdentityException("OAUTH_TOKEN_INVALID");
    }
}
