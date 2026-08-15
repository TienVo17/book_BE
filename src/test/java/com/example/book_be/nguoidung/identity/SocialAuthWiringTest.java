package com.example.book_be.nguoidung.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cac bean cua luong dang nhap social phai gan duoc voi nhau khi TAT CA provider deu tat.
 *
 * Day la lop kiem tra ma cac test bi Docker chan khong lam thay: mot bean con thieu — vi du
 * GoogleTokenExchange chi co interface ma khong co ban cai dat — chi lo ra luc Spring dung
 * context, va khi do server production se khong boot duoc du Google van dang tat.
 *
 * Chi quet dung package identity va khong bat autoconfiguration, de test nay khong can
 * datasource va van chay duoc khi khong co Docker.
 */
@SpringBootTest(classes = SocialAuthWiringTest.Config.class)
@TestPropertySource(properties = {
        "app.auth.google-enabled=false",
        "app.auth.facebook-enabled=false",
        "app.auth.oauth-encryption-key=",
        "app.auth.google-client-id=",
        "app.auth.google-client-secret=",
        "app.auth.google-redirect-uri=",
})
class SocialAuthWiringTest {

    @Configuration
    @ComponentScan(
            basePackageClasses = SocialAuthService.class,
            // Repository JPA can EntityManager that; luong dang nhap duoc kiem tra rieng o
            // cac test service, con o day chi quan tam den cac bean tu viet.
            excludeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {OAuthTransactionService.class, AuthIdentityService.class,
                            SocialSignupService.class, SocialAuthService.class}))
    static class Config {
    }

    @Autowired
    private SocialProviderProperties properties;

    @Autowired
    private GoogleTokenExchange tokenExchange;

    @Autowired
    private GoogleIdTokenVerifier verifier;

    @Test
    void every_social_bean_resolves_while_all_providers_are_disabled() {
        assertThat(properties.isGoogleEnabled()).isFalse();
        // Interface co dung mot ban cai dat: thieu no thi production khong boot duoc.
        assertThat(tokenExchange).isInstanceOf(GoogleHttpTokenExchange.class);
        assertThat(verifier).isNotNull();
    }
}
