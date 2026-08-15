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
 * dong y cua provider, khong bao gio quay ve duoc. Sai cau hinh thi thanh khong khoi dong
 * duoc con hon la hong mot cach am tham.
 *
 * Moi provider duoc kiem doc lap: Google van chay duoc trong khi Facebook con dang cau hinh,
 * va mot app Facebook hong khong keo Google sap theo.
 */
@Component
public class SocialProviderProperties {
    private static final Logger LOGGER = LoggerFactory.getLogger(SocialProviderProperties.class);

    private final boolean googleEnabled;
    private final String googleClientId;
    private final String googleClientSecret;
    private final String googleRedirectUri;

    private final boolean facebookEnabled;
    private final String facebookClientId;
    private final String facebookClientSecret;
    private final String facebookRedirectUri;

    @Autowired
    public SocialProviderProperties(
            @Value("${app.auth.google-enabled:false}") boolean googleEnabled,
            @Value("${app.auth.google-client-id:}") String googleClientId,
            @Value("${app.auth.google-client-secret:}") String googleClientSecret,
            @Value("${app.auth.google-redirect-uri:}") String googleRedirectUri,
            @Value("${app.auth.facebook-enabled:false}") boolean facebookEnabled,
            @Value("${app.auth.facebook-client-id:}") String facebookClientId,
            @Value("${app.auth.facebook-client-secret:}") String facebookClientSecret,
            @Value("${app.auth.facebook-redirect-uri:}") String facebookRedirectUri) {
        if (googleEnabled) {
            requireConfigured(googleClientId, "GOOGLE_CLIENT_ID");
            requireConfigured(googleClientSecret, "GOOGLE_CLIENT_SECRET");
            requireHttpsUrl(googleRedirectUri, "GOOGLE_REDIRECT_URI");
        }
        if (facebookEnabled) {
            requireConfigured(facebookClientId, "FACEBOOK_CLIENT_ID");
            requireConfigured(facebookClientSecret, "FACEBOOK_CLIENT_SECRET");
            requireHttpsUrl(facebookRedirectUri, "FACEBOOK_REDIRECT_URI");
        }
        this.googleEnabled = googleEnabled;
        this.googleClientId = googleClientId;
        this.googleClientSecret = googleClientSecret;
        this.googleRedirectUri = googleRedirectUri;
        this.facebookEnabled = facebookEnabled;
        this.facebookClientId = facebookClientId;
        this.facebookClientSecret = facebookClientSecret;
        this.facebookRedirectUri = facebookRedirectUri;

        // Ghi lai dung nhung gi ung dung DOC DUOC luc khoi dong. Khong co dong nay thi khi
        // mot bien moi truong khong toi duoc ung dung, trieu chung duy nhat la provider im
        // lang tat, va khong ai phan biet duoc "chua dat" voi "dat sai" ma khong phai doan.
        // Chi ghi da-dat/chua-dat, khong bao gio ghi gia tri.
        LOGGER.info("event=social_provider_config google_enabled={} google_client_id={} "
                        + "google_client_secret={} google_redirect_uri={} facebook_enabled={} "
                        + "facebook_client_id={} facebook_client_secret={} facebook_redirect_uri={}",
                googleEnabled, isSet(googleClientId), isSet(googleClientSecret), isSet(googleRedirectUri),
                facebookEnabled, isSet(facebookClientId), isSet(facebookClientSecret),
                isSet(facebookRedirectUri));
    }

    /** Cho test chi quan tam den Google; Facebook mac dinh tat. Khong dung o runtime. */
    SocialProviderProperties(boolean googleEnabled, String googleClientId,
                             String googleClientSecret, String googleRedirectUri) {
        this(googleEnabled, googleClientId, googleClientSecret, googleRedirectUri,
                false, "", "", "");
    }

    private static String isSet(String value) {
        return value == null || value.isBlank() ? "unset" : "set";
    }

    public boolean isGoogleEnabled() { return googleEnabled; }
    public String getGoogleClientId() { return googleClientId; }
    public String getGoogleClientSecret() { return googleClientSecret; }
    public String getGoogleRedirectUri() { return googleRedirectUri; }

    public boolean isFacebookEnabled() { return facebookEnabled; }
    public String getFacebookClientId() { return facebookClientId; }
    public String getFacebookClientSecret() { return facebookClientSecret; }
    public String getFacebookRedirectUri() { return facebookRedirectUri; }

    /** Tra ve cau hinh cua mot provider theo ten, de tang tren khong phai re nhanh theo provider. */
    public ProviderConfig forProvider(String provider) {
        return switch (provider) {
            case "google" -> new ProviderConfig(googleEnabled, googleClientId, googleClientSecret,
                    googleRedirectUri);
            case "facebook" -> new ProviderConfig(facebookEnabled, facebookClientId,
                    facebookClientSecret, facebookRedirectUri);
            default -> throw new AuthIdentityException("PROVIDER_DISABLED");
        };
    }

    private void requireConfigured(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set when that provider is enabled");
        }
    }

    /**
     * Redirect URI phai khop tuyet doi voi gia tri da dang ky ben provider, va phai la https
     * vi authorization code di qua chinh duong nay.
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
                + ", googleClientId=" + isSet(googleClientId)
                + ", googleClientSecret=" + isSet(googleClientSecret)
                + ", googleRedirectUri=" + isSet(googleRedirectUri)
                + ", facebookEnabled=" + facebookEnabled
                + ", facebookClientId=" + isSet(facebookClientId)
                + ", facebookClientSecret=" + isSet(facebookClientSecret)
                + ", facebookRedirectUri=" + isSet(facebookRedirectUri)
                + "}";
    }

    public record ProviderConfig(boolean enabled, String clientId, String clientSecret,
                                 String redirectUri) {
    }
}
