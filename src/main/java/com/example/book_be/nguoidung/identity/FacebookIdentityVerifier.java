package com.example.book_be.nguoidung.identity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Xac minh danh tinh Facebook.
 *
 * Facebook KHONG phat ID token nhu Google, nen khong co chu ky de kiem. Thay vao do phai goi
 * `/debug_token` de hoi chinh Facebook xem token nay thuoc app nao va cua ai, roi doi chieu
 * voi ho so lay ve. Thieu buoc nay thi mot token cua app Facebook khac van dang nhap duoc.
 */
@Component
public class FacebookIdentityVerifier {
    /**
     * Facebook khong phat issuer trong token. Dung mot hang co dinh de khoa danh tinh
     * (provider, issuer, subject) on dinh theo thoi gian; doi gia tri nay se lam moi lien ket
     * da co tro thanh danh tinh khac.
     */
    static final String ISSUER = "https://www.facebook.com";

    private final String appId;

    @Autowired
    public FacebookIdentityVerifier(@Value("${app.auth.facebook-client-id:}") String appId) {
        this.appId = appId;
    }

    /**
     * @param profile ket qua tu Graph `/me`
     * @param debugToken ket qua tu Graph `/debug_token`
     */
    public ProviderIdentity verify(Map<String, Object> profile, Map<String, Object> debugToken) {
        Map<String, Object> data = asMap(debugToken.get("data"));
        if (data == null) {
            throw invalidToken();
        }
        // Token cua mot app Facebook khac van la token that. Khong kiem app_id thi bat ky app
        // nao cung dang nhap duoc vao day.
        if (!constantTimeEquals(string(data.get("app_id")), appId)) {
            throw invalidToken();
        }
        if (!Boolean.TRUE.equals(data.get("is_valid"))) {
            throw invalidToken();
        }

        String tokenSubject = string(data.get("user_id"));
        String profileSubject = string(profile.get("id"));
        if (tokenSubject == null || tokenSubject.isBlank()
                || profileSubject == null || profileSubject.isBlank()) {
            throw invalidToken();
        }
        // Hai lenh goi Graph phai noi ve cung mot nguoi; lech nhau nghia la co gi do da bi
        // trao doi giua chung.
        if (!constantTimeEquals(tokenSubject, profileSubject)) {
            throw invalidToken();
        }

        return new ProviderIdentity(
                "facebook",
                ISSUER,
                profileSubject,
                string(profile.get("email")),
                // Facebook khong cho biet dia chi da duoc xac minh hay chua, va nguoi dung co
                // the tu choi quyen email. Coi la da xac minh se cho phep chiem email nguoi
                // khac, nen o day luon la false; muon dung email thi phai tu gui ma xac minh.
                false,
                string(profile.get("name")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private String string(Object value) {
        return value instanceof String text ? text : null;
    }

    private boolean constantTimeEquals(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private AuthIdentityException invalidToken() {
        return new AuthIdentityException("OAUTH_TOKEN_INVALID");
    }
}
