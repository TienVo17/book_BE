package com.example.book_be.nguoidung.identity;

import com.example.book_be.nguoidung.session.AuthOriginCsrfFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Cac diem vao cua luong dang nhap Google.
 *
 * Callback khong bao gio tra token qua URL. No chi dieu huong ve mot route noi bo trong
 * allowlist kem mot ma ket qua; frontend hoi trang thai qua API chu khong doc gi tu query.
 */
@RestController
@RequestMapping("/tai-khoan/oauth")
public class SocialAuthController {
    /**
     * Buoc luot dang nhap vao dung trinh duyet da bat dau no. HttpOnly de JavaScript khong
     * doc duoc; SameSite=Lax vi cookie nay phai song sot qua redirect quay ve tu Google,
     * ma Strict thi trinh duyet se khong gui kem.
     */
    public static final String BINDING_COOKIE = "__Host-oauth-binding";
    private static final Duration BINDING_LIFETIME = Duration.ofMinutes(15);
    private static final String RESULT_PATH = "/tai-khoan/oauth/ket-qua";

    private final SocialAuthService socialAuthService;
    private final SocialProviderProperties properties;

    public SocialAuthController(SocialAuthService socialAuthService,
                                SocialProviderProperties properties) {
        this.socialAuthService = socialAuthService;
        this.properties = properties;
    }

    /**
     * Frontend dua vao day de quyet dinh co hien nut Google hay khong, thay vi doan tu bien
     * build. Provider tat thi nut khong xuat hien.
     */
    @GetMapping("/trang-thai")
    public ResponseEntity<?> trangThai(HttpServletResponse response) {
        AuthOriginCsrfFilter.applyNoStoreHeaders(response);
        // `cauHinh` cho biet ung dung DOC DUOC nhung bien nao, de phan biet "chua dat bien"
        // voi "dat roi nhung khong toi duoc ung dung" ma khong phai doan qua log.
        // Chi bao da-dat/chua-dat; khong bao gio tra ve gia tri, nhat la client secret.
        return ResponseEntity.ok(Map.of(
                "google", properties.isGoogleEnabled(),
                "facebook", properties.isFacebookEnabled(),
                "cauHinh", Map.of(
                        "clientId", isSet(properties.getGoogleClientId()),
                        "clientSecret", isSet(properties.getGoogleClientSecret()),
                        "redirectUri", isSet(properties.getGoogleRedirectUri())),
                "cauHinhFacebook", Map.of(
                        "clientId", isSet(properties.getFacebookClientId()),
                        "clientSecret", isSet(properties.getFacebookClientSecret()),
                        "redirectUri", isSet(properties.getFacebookRedirectUri()))));
    }

    /** Ten provider la khong ro se tra false, nen route khong ton tai thay vi loi 500. */
    private boolean isEnabled(String provider) {
        return switch (provider) {
            case "google" -> properties.isGoogleEnabled();
            case "facebook" -> properties.isFacebookEnabled();
            default -> false;
        };
    }

    private boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    @GetMapping("/{provider}/start")
    public ResponseEntity<Void> start(@PathVariable String provider, HttpServletResponse response) {
        AuthOriginCsrfFilter.applyNoStoreHeaders(response);
        if (!isEnabled(provider)) {
            // 404 chu khong phai 403: khong tiet lo rang route co ton tai nhung dang tat.
            return ResponseEntity.notFound().build();
        }

        SocialAuthService.Authorization authorization = socialAuthService.startLogin(provider);
        return ResponseEntity.status(302)
                .header(HttpHeaders.SET_COOKIE, bindingCookie(authorization.browserBinding()).toString())
                .header(HttpHeaders.LOCATION, authorization.authorizationUrl())
                .build();
    }

    @GetMapping("/{provider}/callback")
    public ResponseEntity<Void> callback(
            @PathVariable String provider,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "returnTo", required = false) String returnTo,
            HttpServletRequest request,
            HttpServletResponse response) {
        AuthOriginCsrfFilter.applyNoStoreHeaders(response);
        if (!isEnabled(provider)) {
            return ResponseEntity.notFound().build();
        }

        // Duong dan quay ve luon di qua allowlist: day la cho duy nhat ke tan cong co the
        // chen mot URL tuyet doi de bien ung dung thanh open redirect.
        String safeReturn = SocialReturnPath.sanitize(returnTo);
        String binding = cookieValue(request, BINDING_COOKIE);

        if (error != null || code == null || state == null || binding == null) {
            // Nguoi dung tu choi dong y, hoac callback thieu rang buoc trinh duyet. Ca hai
            // deu ket thuc im lang o trang ket qua, khong goi den provider.
            return redirect(safeReturn, "loi");
        }

        try {
            SocialAuthService.CallbackResult result =
                    socialAuthService.completeCallback(provider, code, state, binding);
            return redirect(safeReturn, switch (result.outcome()) {
                case AUTHENTICATED -> "thanh-cong";
                case SIGNUP_REQUIRED -> "can-dang-ky";
                case LINK_REQUIRED -> "can-lien-ket";
            });
        } catch (AuthIdentityException exception) {
            // Ma loi noi bo khong di ra URL: chi mot nhan chung chung, chi tiet nam o log.
            return redirect(safeReturn, "loi");
        }
    }

    /**
     * Moi ket qua deu xoa cookie binding: no dung mot lan, de lai thi mot callback sau co
     * the dung lai chinh rang buoc do.
     */
    private ResponseEntity<Void> redirect(String safeReturn, String ketQua) {
        String location = RESULT_PATH
                + "?ket-qua=" + URLEncoder.encode(ketQua, StandardCharsets.UTF_8)
                + "&tiep-tuc=" + URLEncoder.encode(safeReturn, StandardCharsets.UTF_8);
        return ResponseEntity.status(302)
                .header(HttpHeaders.SET_COOKIE, clearBindingCookie().toString())
                .header(HttpHeaders.LOCATION, location)
                .build();
    }

    private ResponseCookie bindingCookie(String value) {
        return ResponseCookie.from(BINDING_COOKIE, value)
                .path("/").secure(true).httpOnly(true).sameSite("Lax")
                .maxAge(BINDING_LIFETIME)
                .build();
    }

    private ResponseCookie clearBindingCookie() {
        return ResponseCookie.from(BINDING_COOKIE, "")
                .path("/").secure(true).httpOnly(true).sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();
    }

    private String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
