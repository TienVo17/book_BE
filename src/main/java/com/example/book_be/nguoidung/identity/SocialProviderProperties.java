package com.example.book_be.nguoidung.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cau hinh provider, kiem tra ngay luc khoi dong.
 *
 * Thieu credential ma chi phat hien giua luong dang nhap se de nguoi dung mac ket o man hinh
 * dong y cua Google, khong bao gio quay ve duoc. Sai cau hinh thi thanh khong khoi dong duoc
 * con hon la hong mot cach am tham.
 */
@Component
public class SocialProviderProperties {
    private static final Logger LOGGER = LoggerFactory.getLogger(SocialProviderProperties.class);

    private final boolean googleEnabled;
    private final String googleClientId;
    private final String googleClientSecret;
    private final String googleRedirectUri;

    @Autowired
    public SocialProviderProperties(
            @Value("${app.auth.google-enabled:false}") boolean googleEnabled,
            @Value("${app.auth.google-client-id:}") String googleClientId,
            @Value("${app.auth.google-client-secret:}") String googleClientSecret,
            @Value("${app.auth.google-redirect-uri:}") String googleRedirectUri) {
        if (googleEnabled) {
            requireConfigured(googleClientId, "GOOGLE_CLIENT_ID");
            requireConfigured(googleClientSecret, "GOOGLE_CLIENT_SECRET");
            requireHttpsUrl(googleRedirectUri, "GOOGLE_REDIRECT_URI");
        }
        this.googleEnabled = googleEnabled;
        this.googleClientId = googleClientId;
        this.googleClientSecret = googleClientSecret;
        this.googleRedirectUri = googleRedirectUri;

        // Ghi lai dung nhung gi ung dung DOC DUOC luc khoi dong. Khong co dong nay thi khi
        // mot bien moi truong khong toi duoc ung dung, trieu chung duy nhat la provider im
        // lang tat, va khong ai phan biet duoc "chua dat" voi "dat sai" ma khong phai doan.
        // Chi ghi da-dat/chua-dat, khong bao gio ghi gia tri.
        LOGGER.info("event=social_provider_config google_enabled={} client_id={} client_secret={} redirect_uri={}",
                googleEnabled,
                isSet(googleClientId), isSet(googleClientSecret), isSet(googleRedirectUri));
    }

    private static String isSet(String value) {
        return value == null || value.isBlank() ? "unset" : "set";
    }

    public boolean isGoogleEnabled() { return googleEnabled; }
    public String getGoogleClientId() { return googleClientId; }
    public String getGoogleClientSecret() { return googleClientSecret; }
    public String getGoogleRedirectUri() { return googleRedirectUri; }

    private void requireConfigured(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set when that provider is enabled");
        }
    }

    /**
     * Redirect URI phai khop tuyet doi voi gia tri da dang ky ben Google, va phai la https vi
     * authorization code di qua chinh duong nay.
     */
    private void requireHttpsUrl(String value, String name) {
        requireConfigured(value, name);
        if (!value.startsWith("https://")) {
            throw new IllegalStateException(name + " must be an absolute https URL");
        }
    }

    /** Khong bao gio in client secret ra log hay trang loi. */
    @Override
    public String toString() {
        return "SocialProviderProperties{googleEnabled=" + googleEnabled
                + ", googleClientId=" + (googleClientId == null || googleClientId.isBlank() ? "unset" : "set")
                + ", googleClientSecret=" + (googleClientSecret == null || googleClientSecret.isBlank() ? "unset" : "set")
                + ", googleRedirectUri=" + (googleRedirectUri == null || googleRedirectUri.isBlank() ? "unset" : "set")
                + "}";
    }
}
