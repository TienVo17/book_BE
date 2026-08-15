package com.example.book_be.nguoidung.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Goi Graph API cua Facebook de doi code lay danh tinh.
 *
 * Ban Graph API duoc ghim cung: Facebook thay doi hanh vi giua cac ban, va de mac dinh se
 * lam luong dang nhap hong vao mot ngay khong ai doi.
 *
 * Bean nay luon ton tai ke ca khi Facebook dang tat, de ung dung khoi dong duoc; viec chan
 * khi provider tat nam o SocialAuthService.
 */
@Service
public class FacebookHttpTokenExchange implements FacebookTokenExchange {
    private static final Logger LOGGER = LoggerFactory.getLogger(FacebookHttpTokenExchange.class);
    private static final String GRAPH_VERSION = "v21.0";
    private static final String BASE = "https://graph.facebook.com/" + GRAPH_VERSION;
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final SocialProviderProperties properties;

    @Autowired
    public FacebookHttpTokenExchange(SocialProviderProperties properties) {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), properties);
    }

    /** Cho test tiem HttpClient gia; khong dung o runtime. */
    FacebookHttpTokenExchange(HttpClient httpClient, SocialProviderProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    @Override
    public ExchangeResult exchange(String authorizationCode, String codeVerifier, String redirectUri) {
        String appId = properties.getFacebookClientId();
        String appSecret = properties.getFacebookClientSecret();

        Map<String, String> tokenQuery = new LinkedHashMap<>();
        tokenQuery.put("client_id", appId);
        tokenQuery.put("client_secret", appSecret);
        tokenQuery.put("redirect_uri", redirectUri);
        tokenQuery.put("code", authorizationCode);
        // PKCE: khong co verifier thi ke bat duoc authorization code van khong doi duoc token.
        tokenQuery.put("code_verifier", codeVerifier);

        Map<?, ?> tokenBody = getJson(BASE + "/oauth/access_token?" + buildQuery(tokenQuery));
        Object accessToken = tokenBody.get("access_token");
        if (!(accessToken instanceof String token) || token.isBlank()) {
            throw new AuthIdentityException("OAUTH_TOKEN_INVALID");
        }

        // appsecret_proof chung minh loi goi den tu chinh may chu cua app. Thieu no thi mot
        // token bi danh cap van dung duoc tu bat ky dau.
        String proof = appSecretProof(token, appSecret);

        Map<String, String> profileQuery = new LinkedHashMap<>();
        profileQuery.put("fields", "id,name,email");
        profileQuery.put("access_token", token);
        profileQuery.put("appsecret_proof", proof);
        Map<?, ?> profile = getJson(BASE + "/me?" + buildQuery(profileQuery));

        // Facebook khong phat ID token co chu ky, nen phai hoi lai chinh Facebook xem token
        // nay thuoc app nao va cua ai.
        Map<String, String> debugQuery = new LinkedHashMap<>();
        debugQuery.put("input_token", token);
        debugQuery.put("access_token", appId + "|" + appSecret);
        Map<?, ?> debug = getJson(BASE + "/debug_token?" + buildQuery(debugQuery));

        return new ExchangeResult(castMap(profile), castMap(debug));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> value) {
        return (Map<String, Object>) value;
    }

    private Map<?, ?> getJson(String url) {
        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AuthIdentityException("OAUTH_TOKEN_INVALID");
        } catch (Exception exception) {
            // URL chua ca token lan app secret; chi ghi loai loi, khong bao gio ghi URL.
            LOGGER.warn("event=oauth_graph_call_failed provider=facebook exception={}",
                    exception.getClass().getSimpleName());
            throw new AuthIdentityException("OAUTH_TOKEN_INVALID");
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            LOGGER.warn("event=oauth_graph_call_rejected provider=facebook status={}",
                    response.statusCode());
            throw new AuthIdentityException("OAUTH_TOKEN_INVALID");
        }

        try {
            return MAPPER.readValue(response.body(), Map.class);
        } catch (Exception exception) {
            throw new AuthIdentityException("OAUTH_TOKEN_INVALID");
        }
    }

    static String appSecretProof(String accessToken, String appSecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(accessToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    static String buildQuery(Map<String, String> parameters) {
        return parameters.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
