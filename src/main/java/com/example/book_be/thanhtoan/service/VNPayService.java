package com.example.book_be.thanhtoan.service;

import com.example.book_be.thanhtoan.config.VnPayConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class VNPayService {
    private static final ZoneId VIETNAM_TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final VnPayConfig vnPayConfig;
    private final Clock clock;

    @Autowired
    public VNPayService(VnPayConfig vnPayConfig) {
        this(vnPayConfig, Clock.systemUTC());
    }

    VNPayService(VnPayConfig vnPayConfig, Clock clock) {
        this.vnPayConfig = vnPayConfig;
        this.clock = clock;
    }

    public String createOrder(int total, String orderInfo) {
        if (total <= 0) {
            throw new IllegalArgumentException("Payment total must be positive");
        }
        if (orderInfo == null || orderInfo.isBlank()) {
            throw new IllegalArgumentException("Payment order information must not be blank");
        }
        if (vnPayConfig.getTmnCode() == null || vnPayConfig.getTmnCode().isBlank()
                || vnPayConfig.getHashSecret() == null || vnPayConfig.getHashSecret().isBlank()) {
            throw new IllegalStateException("VNPay credentials are not configured");
        }

        Map<String, String> parameters = new HashMap<>();
        parameters.put("vnp_Version", "2.1.0");
        parameters.put("vnp_Command", "pay");
        parameters.put("vnp_TmnCode", vnPayConfig.getTmnCode());
        parameters.put("vnp_Amount", String.valueOf(total * 100L));
        parameters.put("vnp_CurrCode", "VND");
        parameters.put("vnp_TxnRef", vnPayConfig.getRandomNumber(8));
        parameters.put("vnp_OrderInfo", orderInfo);
        parameters.put("vnp_OrderType", "order-type");
        parameters.put("vnp_Locale", "vn");
        parameters.put("vnp_ReturnUrl", vnPayConfig.getReturnUrl());
        parameters.put("vnp_IpAddr", "127.0.0.1");

        ZonedDateTime creationTime = clock.instant().atZone(VIETNAM_TIME_ZONE);
        parameters.put("vnp_CreateDate", VNPAY_DATE_FORMAT.format(creationTime));
        parameters.put("vnp_ExpireDate", VNPAY_DATE_FORMAT.format(creationTime.plusMinutes(15)));

        String query = buildEncodedQuery(parameters);
        String secureHash = vnPayConfig.hmacSHA512(vnPayConfig.getHashSecret(), query);
        return vnPayConfig.getPayUrl() + "?" + query + "&vnp_SecureHash=" + secureHash;
    }

    public int orderReturn(HttpServletRequest request) {
        Map<String, String> fields = new HashMap<>();
        request.getParameterMap().forEach((fieldName, values) -> {
            if (values == null || values.length == 0 || values[0] == null || values[0].isEmpty()) {
                return;
            }
            fields.put(encode(fieldName), encode(values[0]));
        });

        String secureHash = request.getParameter("vnp_SecureHash");
        fields.remove("vnp_SecureHashType");
        fields.remove("vnp_SecureHash");
        String signedFields = vnPayConfig.hashAllFields(fields);
        if (vnPayConfig.getHashSecret() == null || vnPayConfig.getHashSecret().isBlank()
                || signedFields.isEmpty()
                || secureHash == null || secureHash.isBlank()) {
            return -1;
        }
        if (!signedFields.equals(secureHash)) {
            return -1;
        }
        return "00".equals(request.getParameter("vnp_TransactionStatus")) ? 1 : 0;
    }

    private String buildEncodedQuery(Map<String, String> parameters) {
        List<String> fieldNames = parameters.keySet().stream().sorted().toList();
        StringBuilder query = new StringBuilder();
        for (String fieldName : fieldNames) {
            String fieldValue = parameters.get(fieldName);
            if (fieldValue == null || fieldValue.isEmpty()) {
                continue;
            }
            if (!query.isEmpty()) {
                query.append('&');
            }
            query.append(encode(fieldName)).append('=').append(encode(fieldValue));
        }
        return query.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }
}
