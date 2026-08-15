package com.example.book_be.nguoidung.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Doi authorization code lay ID token tai endpoint token cua Google.
 *
 * Bean nay luon ton tai ke ca khi Google dang tat, de ung dung khoi dong duoc; viec chan
 * luong khi provider tat nam o SocialAuthService. Neu dat @ConditionalOnProperty o day thi
 * bat Google se thanh mot thay doi cau hinh co the lam server khong boot.
 */
@Service
public class GoogleHttpTokenExchange implements GoogleTokenExchange {
    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleHttpTokenExchange.class);
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final SocialProviderProperties properties;

    @Autowired
    public GoogleHttpTokenExchange(SocialProviderProperties properties) {
        this(HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), properties);
    }

    /** Cho test tiem HttpClient gia; khong dung o runtime. */
    GoogleHttpTokenExchange(HttpClient httpClient, SocialProviderProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    @Override
    public Map<String, Object> exchange(String authorizationCode, String codeVerifier, String redirectUri) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("code", authorizationCode);
        form.put("client_id", properties.getGoogleClientId());
        form.put("client_secret", properties.getGoogleClientSecret());
        form.put("redirect_uri", redirectUri);
        form.put("grant_type", "authorization_code");
        // PKCE: khong co verifier thi ke bat duoc authorization code van khong doi duoc token.
        form.put("code_verifier", codeVerifier);

        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_ENDPOINT))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(buildFormBody(form), StandardCharsets.UTF_8))
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AuthIdentityException("OAUTH_TOKEN_INVALID");
        } catch (Exception exception) {
            // Than loi cua Google co the chua lai chinh authorization code; chi ghi loai loi.
            LOGGER.warn("event=oauth_token_exchange_failed provider=google exception={}",
                    exception.getClass().getSimpleName());
            throw new AuthIdentityException("OAUTH_TOKEN_INVALID");
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            LOGGER.warn("event=oauth_token_exchange_rejected provider=google status={}",
                    response.statusCode());
            throw new AuthIdentityException("OAUTH_TOKEN_INVALID");
        }

        try {
            Map<?, ?> body = MAPPER.readValue(response.body(), Map.class);
            Object idToken = body.get("id_token");
            // Chi lay claim tu id_token roi bo toan bo phan con lai cua phan hoi: access_token
            // va refresh_token cua Google khong duoc di tiep vao ung dung.
            return decodeIdTokenClaims(idToken instanceof String text ? text : null);
        } catch (AuthIdentityException rethrown) {
            throw rethrown;
        } catch (Exception exception) {
            throw new AuthIdentityException("OAUTH_TOKEN_INVALID");
        }
    }

    /**
     * Doc phan payload cua ID token.
     *
     * Chu ky da duoc TLS toi thang endpoint cua Google bao dam trong luong nay: phan hoi den
     * truc tiep tu oauth2.googleapis.com chu khong qua trinh duyet, nen khong co ben thu ba
     * nao chen token vao giua. Cac kiem tra issuer/audience/expiry van do
     * GoogleIdTokenVerifier lam sau do.
     */
    static Map<String, Object> decodeIdTokenClaims(String idToken) {
        if (idToken == null) {
            throw new AuthIdentityException("OAUTH_TOKEN_INVALID");
        }
        String[] parts = idToken.split("\\.");
        if (parts.length != 3) {
            throw new AuthIdentityException("OAUTH_TOKEN_INVALID");
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = MAPPER.readValue(payload, Map.class);
            return claims;
        } catch (Exception exception) {
            throw new AuthIdentityException("OAUTH_TOKEN_INVALID");
        }
    }

    static String buildFormBody(Map<String, String> form) {
        return form.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
