package com.example.book_be.shared.web;

import com.example.book_be.shared.config.FrontendUrlProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * A temporary, feature-gated dummy endpoint used only to rehearse same-origin proxy behavior.
 * It deliberately accepts no production credentials and never reflects request secrets.
 */
@RestController
@RequestMapping("/tai-khoan/_proxy-rehearsal")
public class ProxyRehearsalController {
    public static final String ISSUE_PATH = "/tai-khoan/_proxy-rehearsal/issue";
    public static final String REDIRECT_PATH = "/tai-khoan/_proxy-rehearsal/redirect";
    public static final String COMPLETE_PATH = "/tai-khoan/_proxy-rehearsal/complete";

    private static final String COOKIE_NAME = "__Host-proxy-rehearsal";
    private static final String DUMMY_AUTHORIZATION = "Bearer proxy-rehearsal-dummy";
    private static final String DUMMY_BODY = "{\"probe\":\"proxy-rehearsal\"}";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final boolean enabled;
    private final FrontendUrlProvider frontendUrlProvider;

    public ProxyRehearsalController(
            @Value("${app.auth.proxy-rehearsal-enabled:false}") boolean enabled,
            FrontendUrlProvider frontendUrlProvider) {
        this.enabled = enabled;
        this.frontendUrlProvider = frontendUrlProvider;
    }

    @PostMapping(value = "/issue", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> issue(HttpServletRequest request, HttpServletResponse response,
                                   @RequestBody String body) {
        if (!enabled) {
            return notFound(request);
        }
        if (!isCanonicalOrigin(request.getHeader(HttpHeaders.ORIGIN))) {
            return rejectedOrigin(request);
        }

        boolean authorizationAccepted = DUMMY_AUTHORIZATION.equals(request.getHeader(HttpHeaders.AUTHORIZATION));
        boolean bodyAccepted = DUMMY_BODY.equals(body);
        response.addHeader(HttpHeaders.SET_COOKIE, issueCookie());

        return ResponseEntity.accepted()
                .body(Map.of(
                        "authorizationAccepted", authorizationAccepted,
                        "bodyAccepted", bodyAccepted,
                        "cookieIssued", true,
                        "requestDigest", safeDigest(authorizationAccepted, bodyAccepted),
                        "traceId", RequestTraceFilter.currentTraceId(request)
                ));
    }

    @GetMapping("/redirect")
    public ResponseEntity<?> redirect(HttpServletRequest request) {
        if (!enabled) {
            return notFound(request);
        }
        if (!hasProbeCookie(request)) {
            return ResponseEntity.badRequest().body(safeError(request, "PROXY_REHEARSAL_COOKIE_REQUIRED"));
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, COMPLETE_PATH)
                .build();
    }

    @GetMapping(value = "/complete", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> complete(HttpServletRequest request, HttpServletResponse response) {
        if (!enabled) {
            return notFound(request);
        }
        boolean cookieSeen = hasProbeCookie(request);
        response.addHeader(HttpHeaders.SET_COOKIE, clearCookie());
        return ResponseEntity.ok(Map.of(
                "cookieSeen", cookieSeen,
                "traceId", RequestTraceFilter.currentTraceId(request)
        ));
    }

    private ResponseEntity<Map<String, Object>> notFound(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.<String, Object>of(
                        "code", "NOT_FOUND",
                        "message", "Không tìm thấy tài nguyên.",
                        "traceId", RequestTraceFilter.currentTraceId(request)
                ));
    }

    private ResponseEntity<Map<String, Object>> rejectedOrigin(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(safeError(request, "PROXY_REHEARSAL_ORIGIN_REJECTED"));
    }

    private Map<String, Object> safeError(HttpServletRequest request, String code) {
        return Map.of(
                "code", code,
                "message", "Yêu cầu rehearsal không hợp lệ.",
                "traceId", RequestTraceFilter.currentTraceId(request)
        );
    }

    private boolean isCanonicalOrigin(String origin) {
        return frontendUrlProvider.getFrontendUrl().equals(origin);
    }

    private boolean hasProbeCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private String issueCookie() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return COOKIE_NAME + "=" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
                + "; Path=/; Secure; HttpOnly; SameSite=Lax";
    }

    private String clearCookie() {
        return COOKIE_NAME + "=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Lax";
    }

    private String safeDigest(boolean authorizationAccepted, boolean bodyAccepted) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((authorizationAccepted + ":" + bodyAccepted)
                    .getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

}
