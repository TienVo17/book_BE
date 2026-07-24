package com.example.book_be.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Normalizes the configured frontend origin and builds browser-facing links.
 */
@Component
public class FrontendUrlProvider {
    private static final String ACTIVATION_PATH = "kich-hoat";
    private static final String RESET_PASSWORD_PATH = "dat-lai-mat-khau";
    private static final String VNPAY_RETURN_PATH = "xu-ly-kq-thanh-toan";

    private final String frontendUrl;

    public FrontendUrlProvider(@Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.frontendUrl = normalizeOrigin(frontendUrl);
    }

    public String getFrontendUrl() {
        return frontendUrl;
    }

    public String activationUrl(String email, String maKichHoat) {
        return buildPath(ACTIVATION_PATH, email, maKichHoat);
    }

    public String resetPasswordUrl(String email, String token) {
        return buildPath(RESET_PASSWORD_PATH, email, token);
    }

    public String vnPayReturnUrl() {
        return buildPath(VNPAY_RETURN_PATH);
    }

    public static String normalizeOrigin(String rawUrl) {
        URI uri = parseHttpUrl(rawUrl, "app.frontend-url");
        String path = uri.getRawPath();
        if (path != null && !path.isEmpty() && !"/".equals(path)) {
            throw new IllegalArgumentException("app.frontend-url must not include a path");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("app.frontend-url must not include a query or fragment");
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = normalizeHost(uri.getHost());
        int port = normalizePort(scheme, uri.getPort());
        try {
            return new URI(scheme, null, host, port, null, null, null).toASCIIString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("app.frontend-url is invalid", exception);
        }
    }

    public static String normalizeReturnUrl(String rawUrl) {
        URI uri = parseHttpUrl(rawUrl, "vnpay.return-url");
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("vnpay.return-url must not include a query or fragment");
        }

        URI asciiUri = URI.create(uri.toASCIIString());
        String scheme = asciiUri.getScheme().toLowerCase(Locale.ROOT);
        String host = normalizeHost(asciiUri.getHost());
        int port = normalizePort(scheme, asciiUri.getPort());
        String path = asciiUri.getRawPath();
        if (path != null && path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        String authority;
        try {
            authority = new URI(scheme, null, host, port, null, null, null).toASCIIString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("vnpay.return-url is invalid", exception);
        }
        return authority + (path == null ? "" : path);
    }

    private String buildPath(String path, String... segments) {
        StringBuilder url = new StringBuilder(frontendUrl).append('/').append(path);
        for (String segment : segments) {
            if (segment == null || segment.isBlank()) {
                throw new IllegalArgumentException("Frontend link path segments must not be blank");
            }
            url.append('/').append(UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8));
        }
        return url.toString();
    }

    private static String normalizeHost(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.startsWith("[") && normalizedHost.endsWith("]")) {
            return normalizedHost.substring(1, normalizedHost.length() - 1);
        }
        return normalizedHost;
    }

    private static int normalizePort(String scheme, int port) {
        if (("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80)) {
            return -1;
        }
        return port;
    }

    private static URI parseHttpUrl(String rawUrl, String propertyName) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException(propertyName + " must not be blank");
        }
        try {
            URI uri = new URI(rawUrl.trim());
            if ((!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException(propertyName + " must be an absolute HTTP(S) URL without credentials");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(propertyName + " is invalid", exception);
        }
    }
}
