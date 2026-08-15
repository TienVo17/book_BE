package com.example.book_be.nguoidung.identity;

import com.example.book_be.nguoidung.domain.NguoiDung;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Dieu phoi mot luot dang nhap qua provider: dung trang thai mot lan tu
 * OAuthTransactionService, doi code lay claim, xac minh claim, roi phan giai ra danh tinh
 * ung dung.
 *
 * Mot lop cho ca hai provider chu khong phai hai stack rieng: state/PKCE/browser binding,
 * thu tu kiem tra va ba ket qua tra ve deu giong nhau. Chi phan "doi code lay danh tinh" la
 * khac, vi Google phat ID token con Facebook phai hoi lai Graph API.
 *
 * Service nay khong tao tai khoan. Viec do nam o SocialSignupService, chay o buoc hoan tat
 * rieng sau khi nguoi dung dien xong ho so.
 */
@Service
public class SocialAuthService {
    private static final String GOOGLE_AUTHORIZATION_ENDPOINT =
            "https://accounts.google.com/o/oauth2/v2/auth";
    /** Ghim ban Graph: de mac dinh se lam luong dang nhap hong vao mot ngay khong ai doi. */
    private static final String FACEBOOK_AUTHORIZATION_ENDPOINT =
            "https://www.facebook.com/v21.0/dialog/oauth";

    /** Chi xin danh tinh. Xin them scope la xin quyen doc du lieu khong lien quan den dang nhap. */
    private static final String GOOGLE_SCOPES = "openid profile email";
    private static final String FACEBOOK_SCOPES = "public_profile,email";

    private final OAuthTransactionService transactionService;
    private final GoogleTokenExchange googleTokenExchange;
    private final GoogleIdTokenVerifier googleVerifier;
    private final FacebookTokenExchange facebookTokenExchange;
    private final FacebookIdentityVerifier facebookVerifier;
    private final AuthIdentityService identityService;
    private final SocialProviderProperties properties;

    public SocialAuthService(OAuthTransactionService transactionService,
                             GoogleTokenExchange googleTokenExchange,
                             GoogleIdTokenVerifier googleVerifier,
                             FacebookTokenExchange facebookTokenExchange,
                             FacebookIdentityVerifier facebookVerifier,
                             AuthIdentityService identityService,
                             SocialProviderProperties properties) {
        this.transactionService = transactionService;
        this.googleTokenExchange = googleTokenExchange;
        this.googleVerifier = googleVerifier;
        this.facebookTokenExchange = facebookTokenExchange;
        this.facebookVerifier = facebookVerifier;
        this.identityService = identityService;
        this.properties = properties;
    }

    public Authorization startLogin(String provider) {
        return start(provider, OAuthFlowKind.LOGIN, null);
    }

    public Authorization startLink(String provider, int targetUserId) {
        return start(provider, OAuthFlowKind.LINK, targetUserId);
    }

    private Authorization start(String provider, OAuthFlowKind flowKind, Integer targetUserId) {
        SocialProviderProperties.ProviderConfig config = requireEnabled(provider);
        OAuthTransactionService.StartedFlow flow =
                transactionService.start(provider, flowKind, config.redirectUri(), targetUserId);

        String url = authorizationEndpoint(provider)
                + "?client_id=" + encode(config.clientId())
                + "&redirect_uri=" + encode(config.redirectUri())
                + "&response_type=code"
                + "&scope=" + encode(scopes(provider))
                + "&state=" + encode(flow.state())
                + "&code_challenge=" + encode(flow.codeChallenge())
                + "&code_challenge_method=S256";
        if ("google".equals(provider)) {
            // Nonce chi thuoc OpenID Connect; Facebook khong phat ID token nen khong dung.
            url = url + "&nonce=" + encode(flow.nonce());
        }
        return new Authorization(url, flow.browserBinding());
    }

    public CallbackResult completeCallback(String provider, String authorizationCode,
                                           String state, String browserBinding) {
        requireEnabled(provider);
        // Xac minh state truoc khi cham vao code: neu doi code roi moi kiem tra thi mot
        // callback bi phat lai van tieu mat mot authorization code that o phia provider.
        OAuthTransaction transaction = transactionService.consume(state, browserBinding, provider);
        String codeVerifier = transactionService.decryptVerifier(transaction);

        ProviderIdentity identity = "google".equals(provider)
                ? verifyGoogle(authorizationCode, codeVerifier, transaction.getRedirectUri())
                : verifyFacebook(authorizationCode, codeVerifier, transaction.getRedirectUri());

        AuthIdentityService.Resolution resolution = identityService.resolve(identity);
        if (resolution.linkedUser() != null) {
            return new CallbackResult(Outcome.AUTHENTICATED, resolution.linkedUser(), identity, transaction);
        }
        if (resolution.collidesWithExistingAccount()) {
            // Khong tu lien ket. Nguoi dung phai dang nhap bang mat khau de chung minh so huu.
            return new CallbackResult(Outcome.LINK_REQUIRED, null, identity, transaction);
        }
        return new CallbackResult(Outcome.SIGNUP_REQUIRED, null, identity, transaction);
    }

    private ProviderIdentity verifyGoogle(String code, String codeVerifier, String redirectUri) {
        Map<String, Object> claims = googleTokenExchange.exchange(code, codeVerifier, redirectUri);
        return googleVerifier.verify(claims, null);
    }

    private ProviderIdentity verifyFacebook(String code, String codeVerifier, String redirectUri) {
        FacebookTokenExchange.ExchangeResult result =
                facebookTokenExchange.exchange(code, codeVerifier, redirectUri);
        return facebookVerifier.verify(result.profile(), result.debugToken());
    }

    private String authorizationEndpoint(String provider) {
        return "google".equals(provider)
                ? GOOGLE_AUTHORIZATION_ENDPOINT
                : FACEBOOK_AUTHORIZATION_ENDPOINT;
    }

    private String scopes(String provider) {
        return "google".equals(provider) ? GOOGLE_SCOPES : FACEBOOK_SCOPES;
    }

    /**
     * Ten provider la nguon duy nhat quyet dinh cau hinh nao duoc dung. Ten la se roi vao
     * nhanh mac dinh cua forProvider va bi tu choi, khong bao gio chay tiep.
     */
    private SocialProviderProperties.ProviderConfig requireEnabled(String provider) {
        SocialProviderProperties.ProviderConfig config = properties.forProvider(provider);
        if (!config.enabled()) {
            throw new AuthIdentityException("PROVIDER_DISABLED");
        }
        return config;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public enum Outcome {
        AUTHENTICATED,
        SIGNUP_REQUIRED,
        LINK_REQUIRED
    }

    /**
     * `browserBinding` duoc dat vao mot cookie ngan han truoc khi chuyen huong, de callback
     * chung minh duoc chinh trinh duyet nay da bat dau luot dang nhap.
     */
    public record Authorization(String authorizationUrl, String browserBinding) {
    }

    public record CallbackResult(Outcome outcome, NguoiDung user, ProviderIdentity identity,
                                 OAuthTransaction transaction) {
    }
}
