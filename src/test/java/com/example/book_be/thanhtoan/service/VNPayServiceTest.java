package com.example.book_be.thanhtoan.service;

import com.example.book_be.shared.config.FrontendUrlProvider;
import com.example.book_be.thanhtoan.config.VnPayConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class VNPayServiceTest {

    @Test
    void spring_uses_the_production_constructor() {
        VnPayConfig config = new VnPayConfig(
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "",
                "merchant-code",
                "hash-secret",
                "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction",
                new FrontendUrlProvider("https://frontend.example"));

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(VnPayConfig.class, () -> config);
            context.register(VNPayService.class);
            context.refresh();

            assertThat(context.getBean(VNPayService.class)).isNotNull();
        }
    }

    @Test
    void uses_explicitly_configured_return_url_in_signed_payment_request() {
        VNPayService service = new VNPayService(new VnPayConfig(
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "https://payments.example/return/",
                "merchant-code",
                "hash-secret",
                "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction",
                new FrontendUrlProvider("https://frontend.example")));

        Map<String, String> parameters = UriComponentsBuilder.fromUriString(service.createOrder(25_000, "123"))
                .build()
                .getQueryParams()
                .toSingleValueMap();

        assertThat(parameters.get("vnp_ReturnUrl")).isEqualTo("https%3A%2F%2Fpayments.example%2Freturn");
        assertThat(parameters.get("vnp_SecureHash")).isNotBlank();
    }

    @Test
    void defaults_return_url_to_frontend_payment_result_route() {
        VNPayService service = new VNPayService(new VnPayConfig(
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "",
                "merchant-code",
                "hash-secret",
                "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction",
                new FrontendUrlProvider("https://frontend.example/")));

        Map<String, String> parameters = UriComponentsBuilder.fromUriString(service.createOrder(25_000, "123"))
                .build()
                .getQueryParams()
                .toSingleValueMap();

        assertThat(parameters.get("vnp_ReturnUrl"))
                .isEqualTo("https%3A%2F%2Ffrontend.example%2Fxu-ly-kq-thanh-toan");
    }

    @Test
    void generates_vnpay_dates_in_vietnam_time_with_fifteen_minute_expiry() {
        VnPayConfig config = new VnPayConfig(
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "",
                "merchant-code",
                "hash-secret",
                "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction",
                new FrontendUrlProvider("https://frontend.example"));
        VNPayService service = new VNPayService(
                config,
                Clock.fixed(Instant.parse("2026-07-24T02:30:00Z"), ZoneOffset.UTC));

        Map<String, String> parameters = UriComponentsBuilder.fromUriString(service.createOrder(25_000, "123"))
                .build()
                .getQueryParams()
                .toSingleValueMap();

        assertThat(parameters.get("vnp_CreateDate")).isEqualTo("20260724093000");
        assertThat(parameters.get("vnp_ExpireDate")).isEqualTo("20260724094500");
    }

    @Test
    void rejects_pay_url_with_query_or_fragment() {
        assertThatIllegalArgumentException().isThrownBy(() -> new VnPayConfig(
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?locale=vi",
                "",
                "merchant-code",
                "hash-secret",
                "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction",
                new FrontendUrlProvider("https://frontend.example")))
                .withMessage("vnpay.pay-url must not include a query or fragment");
    }
}
