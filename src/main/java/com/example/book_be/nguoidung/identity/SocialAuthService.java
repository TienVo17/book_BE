package com.example.book_be.nguoidung.identity;

import com.example.book_be.nguoidung.domain.NguoiDung;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Dieu phoi mot luot dang nhap Google: dung trang thai mot lan tu OAuthTransactionService,
 * doi code lay claim, xac minh claim, rồi phan giai ra danh tinh ung dung.
 *
 * Service nay khong tao tai khoan. Callback chi noi duoc ba ket qua: da co tai khoan lien
 * ket, can dang ky, hoac trung email nen can chung minh so huu. Viec tao tai khoan nam o
 * SocialSignupService, chay o buoc hoan tat rieng.
 */
@Service
public class SocialAuthService {
    private static final String AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    /** Chi xin danh tinh. Xin them scope la xin quyen doc du lieu khong lien quan gi den dang nhap. */
    private static final String SCOPES = "openid profile email";

    private final OAuthTransactionService transactionService;
    private final GoogleTokenExchange tokenExchange;
    private final GoogleIdTokenVerifier verifier;
    private final AuthIdentityService identityService;
    private final SocialProviderProperties properties;

    public SocialAuthService(OAuthTransactionService transactionService,
                             GoogleTokenExchange tokenExchange,
                             GoogleIdTokenVerifier verifier,
                             AuthIdentityService identityService,
                             SocialProviderProperties properties) {
        this.transactionService = transactionService;
        this.tokenExchange = tokenExchange;
        this.verifier = verifier;
        this.identityService = identityService;
        this.properties = properties;
    }

    public Authorization startLogin() {
        return start(OAuthFlowKind.LOGIN, null);
    }

    public Authorization startLink(int targetUserId) {
        return start(OAuthFlowKind.LINK, targetUserId);
    }

    private Authorization start(OAuthFlowKind flowKind, Integer targetUserId) {
        requireGoogleEnabled();
        String redirectUri = properties.getGoogleRedirectUri();
        OAuthTransactionService.StartedFlow flow =
                transactionService.start("google", flowKind, redirectUri, targetUserId);

        String url = AUTHORIZATION_ENDPOINT
                + "?client_id=" + encode(properties.getGoogleClientId())
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=" + encode(SCOPES)
                + "&state=" + encode(flow.state())
                + "&nonce=" + encode(flow.nonce())
                + "&code_challenge=" + encode(flow.codeChallenge())
                + "&code_challenge_method=S256";
        return new Authorization(url, flow.browserBinding());
    }

    public CallbackResult completeCallback(String authorizationCode, String state, String browserBinding) {
        requireGoogleEnabled();
        // Xac minh state truoc khi cham vao code: neu doi code roi moi kiem tra thi mot
        // callback bi phat lai van tieu mat mot authorization code that o phia Google.
        OAuthTransaction transaction = transactionService.consume(state, browserBinding, "google");

        String codeVerifier = transactionService.decryptVerifier(transaction);
        Map<String, Object> claims = tokenExchange.exchange(
                authorizationCode, codeVerifier, transaction.getRedirectUri());
        ProviderIdentity identity = verifier.verify(claims, null);

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

    private void requireGoogleEnabled() {
        if (!properties.isGoogleEnabled()) {
            throw new AuthIdentityException("PROVIDER_DISABLED");
        }
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
