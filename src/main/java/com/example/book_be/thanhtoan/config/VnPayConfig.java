package com.example.book_be.thanhtoan.config;

import com.example.book_be.shared.config.FrontendUrlProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class VnPayConfig {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String NUMERIC_CHARACTERS = "0123456789";

    private final String payUrl;
    private final String returnUrl;
    private final String tmnCode;
    private final String hashSecret;
    private final String apiUrl;

    public VnPayConfig(
            @Value("${vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String payUrl,
            @Value("${vnpay.return-url:}") String configuredReturnUrl,
            @Value("${vnpay.tmn-code:}") String tmnCode,
            @Value("${vnpay.hash-secret:}") String hashSecret,
            @Value("${vnpay.api-url:https://sandbox.vnpayment.vn/merchant_webapi/api/transaction}") String apiUrl,
            FrontendUrlProvider frontendUrlProvider) {
        this.payUrl = normalizeHttpUrl(payUrl, "vnpay.pay-url");
        this.returnUrl = configuredReturnUrl == null || configuredReturnUrl.isBlank()
                ? frontendUrlProvider.vnPayReturnUrl()
                : FrontendUrlProvider.normalizeReturnUrl(configuredReturnUrl);
        this.tmnCode = tmnCode;
        this.hashSecret = hashSecret;
        this.apiUrl = normalizeHttpUrl(apiUrl, "vnpay.api-url");
    }

    public String getPayUrl() {
        return payUrl;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public String getTmnCode() {
        return tmnCode;
    }

    public String getHashSecret() {
        return hashSecret;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String hashAllFields(Map<String, String> fields) {
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        fieldNames.sort(String::compareTo);
        StringBuilder hashData = new StringBuilder();
        for (String fieldName : fieldNames) {
            String fieldValue = fields.get(fieldName);
            if (fieldValue == null || fieldValue.isEmpty()) {
                continue;
            }
            if (!hashData.isEmpty()) {
                hashData.append('&');
            }
            hashData.append(fieldName).append('=').append(fieldValue);
        }
        return hmacSHA512(hashSecret, hashData.toString());
    }

    public String hmacSHA512(String key, String data) {
        if (key == null || key.isBlank() || data == null) {
            return "";
        }
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            hmac512.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] result = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(2 * result.length);
            for (byte value : result) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (Exception exception) {
            return "";
        }
    }

    public String getRandomNumber(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Random number length must be positive");
        }
        StringBuilder randomNumber = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            randomNumber.append(NUMERIC_CHARACTERS.charAt(RANDOM.nextInt(NUMERIC_CHARACTERS.length())));
        }
        return randomNumber.toString();
    }

    private static String normalizeHttpUrl(String rawUrl, String propertyName) {
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
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException(propertyName + " must not include a query or fragment");
            }
            return uri.toASCIIString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(propertyName + " is invalid", exception);
        }
    }
}
