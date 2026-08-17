package com.example.book_be.nguoidung.identity;

import com.example.book_be.nguoidung.baomat.JwtService;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.session.AuthOriginCsrfFilter;
import com.example.book_be.nguoidung.session.RefreshCookieFactory;
import com.example.book_be.nguoidung.session.RefreshSessionService;
import com.example.book_be.nguoidung.session.SessionPrincipal;
import com.example.book_be.shared.email.EmailService;
import com.example.book_be.shared.security.JwtResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /**
     * Ho so dang ky dang do. Cung ly do HttpOnly/SameSite=Lax nhu cookie binding: no phai
     * song sot qua redirect quay ve tu provider, va JavaScript khong duoc doc.
     */
    public static final String INTENT_COOKIE = "__Host-oauth-intent";
    private static final Duration BINDING_LIFETIME = Duration.ofMinutes(15);
    private static final Duration INTENT_LIFETIME = Duration.ofMinutes(30);
    private static final String RESULT_PATH = "/tai-khoan/oauth/ket-qua";
    private static final Logger LOGGER = LoggerFactory.getLogger(SocialAuthController.class);

    private final SocialAuthService socialAuthService;
    private final SocialProviderProperties properties;
    private final SocialSignupIntentService intentService;
    private final SocialSignupService signupService;
    private final RefreshSessionService refreshSessionService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final JwtService jwtService;
    private final EmailService emailService;

    public SocialAuthController(SocialAuthService socialAuthService,
                                SocialProviderProperties properties,
                                SocialSignupIntentService intentService,
                                SocialSignupService signupService,
                                RefreshSessionService refreshSessionService,
                                RefreshCookieFactory refreshCookieFactory,
                                JwtService jwtService,
                                EmailService emailService) {
        this.socialAuthService = socialAuthService;
        this.properties = properties;
        this.intentService = intentService;
        this.signupService = signupService;
        this.refreshSessionService = refreshSessionService;
        this.refreshCookieFactory = refreshCookieFactory;
        this.jwtService = jwtService;
        this.emailService = emailService;
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
            return redirect(safeReturn, "loi", null);
        }

        try {
            SocialAuthService.CallbackResult result =
                    socialAuthService.completeCallback(provider, code, state, binding);
            return switch (result.outcome()) {
                case AUTHENTICATED -> redirect(safeReturn, "thanh-cong",
                        issueSessionCookie(result.user()));
                // Danh tinh da xac minh duoc giao lai cho buoc hoan tat qua mot ho so dang do
                // ben server. Khong tao `nguoi_dung` o day: bo ngang giua chung se de lai mot
                // tai khoan khong dang nhap lai duoc ma van chiem cho username/email.
                case SIGNUP_REQUIRED -> redirect(safeReturn, "can-dang-ky",
                        intentCookie(intentService.create(result.identity())));
                case LINK_REQUIRED -> redirect(safeReturn, "can-lien-ket", null);
            };
        } catch (AuthIdentityException exception) {
            // Ma loi noi bo khong di ra URL: chi mot nhan chung chung, chi tiet nam o log.
            return redirect(safeReturn, "loi", null);
        }
    }

    /**
     * Phat phien cho tai khoan da lien ket san. Refresh cookie mac dinh khong ghi nho: nguoi
     * dung khong duoc hoi o luong provider, va chon dai han thay ho la tu quyet dinh tang
     * thoi gian song cua phien.
     */
    private ResponseCookie issueSessionCookie(NguoiDung user) {
        RefreshSessionService.SessionGrant grant = refreshSessionService.issueIfEnabled(user, false);
        return grant == null ? null : refreshCookieFactory.issue(grant.rawToken(), grant.rememberMe());
    }

    /**
     * Moi ket qua deu xoa cookie binding: no dung mot lan, de lai thi mot callback sau co
     * the dung lai chinh rang buoc do.
     */
    private ResponseEntity<Void> redirect(String safeReturn, String ketQua, ResponseCookie extra) {
        String location = RESULT_PATH
                + "?ket-qua=" + URLEncoder.encode(ketQua, StandardCharsets.UTF_8)
                + "&tiep-tuc=" + URLEncoder.encode(safeReturn, StandardCharsets.UTF_8);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(302);
        builder.header(HttpHeaders.SET_COOKIE, clearBindingCookie().toString());
        if (extra != null) {
            builder.header(HttpHeaders.SET_COOKIE, extra.toString());
        }
        return builder.header(HttpHeaders.LOCATION, location).build();
    }

    /**
     * Du lieu de dung form hoan tat dang ky. Khong tra ve token cua provider hay bat ky bi
     * mat nao: chi ten hien thi va dia chi da co, kem viec dia chi do da co bang chung chua.
     */
    @GetMapping("/dang-ky-cho")
    public ResponseEntity<?> hoSoDangCho(HttpServletRequest request, HttpServletResponse response) {
        AuthOriginCsrfFilter.applyNoStoreHeaders(response);
        OAuthSignupIntent intent = intentService.require(cookieValue(request, INTENT_COOKIE));
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("provider", intent.getProvider());
        body.put("email", intent.getEmail());
        body.put("emailDaXacMinh", intent.isEmailVerified());
        body.put("tenHienThi", intent.getTenHienThi());
        return ResponseEntity.ok(body);
    }

    /**
     * Gui ma xac minh toi dia chi nguoi dung chon.
     *
     * Bat buoc khi provider khong chung minh duoc dia chi - Facebook thi khong bao gio chung
     * minh duoc. Phan hoi luon giong nhau du gui duoc hay khong, de khong bien endpoint nay
     * thanh cong cu do xem dia chi nao ton tai.
     */
    @PostMapping("/gui-ma-xac-minh-email")
    public ResponseEntity<?> guiMaXacMinhEmail(@RequestBody Map<String, String> body,
                                               HttpServletRequest request,
                                               HttpServletResponse response) {
        AuthOriginCsrfFilter.applyNoStoreHeaders(response);
        String token = cookieValue(request, INTENT_COOKIE);
        String email = SocialSignupIntentService.normalizeEmail(body.get("email"));
        if (email == null) {
            throw new AuthIdentityException("EMAIL_REQUIRED");
        }
        String code = intentService.startEmailVerification(token, email);
        try {
            emailService.ensureConfigured();
            emailService.sendEmail(email, "Mã xác minh đăng ký",
                    "Mã xác minh của bạn là " + code + ". Mã có hiệu lực trong 10 phút.");
        } catch (RuntimeException exception) {
            // Ma da nam trong ho so roi; that bai gui khong duoc lam lo dia chi nao ton tai.
            // Chi ghi loai loi, khong bao gio ghi dia chi hay ma.
            LOGGER.warn("event=email_failed type=social_signup_code exception={}",
                    exception.getClass().getSimpleName());
        }
        return ResponseEntity.ok(Map.of("daGui", true));
    }

    @PostMapping("/xac-minh-email")
    public ResponseEntity<?> xacMinhEmail(@RequestBody Map<String, String> body,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {
        AuthOriginCsrfFilter.applyNoStoreHeaders(response);
        intentService.confirmEmail(cookieValue(request, INTENT_COOKIE), body.get("ma"));
        return ResponseEntity.ok(Map.of("emailDaXacMinh", true));
    }

    /**
     * Tao tai khoan roi phat phien ngay, de nguoi dung khong phai dang nhap lai lan nua.
     *
     * Ho so bi tieu truoc khi tao tai khoan: gui lai cung mot form hai lan phai that bai o
     * lan thu hai chu khong tao them tai khoan.
     */
    @PostMapping("/hoan-tat-dang-ky")
    public ResponseEntity<?> hoanTatDangKy(@RequestBody Map<String, Object> body,
                                           HttpServletRequest request,
                                           HttpServletResponse response) {
        AuthOriginCsrfFilter.applyNoStoreHeaders(response);
        OAuthSignupIntent intent = intentService.consume(cookieValue(request, INTENT_COOKIE));
        boolean ghiNho = Boolean.TRUE.equals(body.get("ghiNho"));

        NguoiDung user = signupService.complete(intent, new SocialSignupService.CompletionRequest(
                text(body.get("tenDangNhap")),
                SocialSignupIntentService.normalizeEmail(text(body.get("email"))),
                text(body.get("hoDem")),
                text(body.get("ten"))));

        RefreshSessionService.SessionGrant grant = refreshSessionService.issueIfEnabled(user, ghiNho);
        if (grant != null) {
            response.addHeader(HttpHeaders.SET_COOKIE,
                    refreshCookieFactory.issue(grant.rawToken(), grant.rememberMe()).toString());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, clearIntentCookie().toString());
        return ResponseEntity.ok(new JwtResponse(
                jwtService.generateToken(user), jwtService.getExpirationSeconds(),
                SessionPrincipal.from(user)));
    }

    /**
     * Ma loi on dinh de frontend re nhanh; khong kem chi tiet nao noi duoc tai khoan nao
     * ton tai. Callback tu bat ngoai le cua no nen khong di qua day.
     */
    @ExceptionHandler(AuthIdentityException.class)
    public ResponseEntity<?> xuLyLoiDanhTinh(AuthIdentityException exception,
                                             HttpServletResponse response) {
        AuthOriginCsrfFilter.applyNoStoreHeaders(response);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("code", exception.getCode()));
    }

    private static String text(Object value) {
        return value instanceof String string ? string : null;
    }

    private ResponseCookie intentCookie(String value) {
        return ResponseCookie.from(INTENT_COOKIE, value)
                .path("/").secure(true).httpOnly(true).sameSite("Lax")
                .maxAge(INTENT_LIFETIME)
                .build();
    }

    private ResponseCookie clearIntentCookie() {
        return ResponseCookie.from(INTENT_COOKIE, "")
                .path("/").secure(true).httpOnly(true).sameSite("Lax")
                .maxAge(Duration.ZERO)
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
