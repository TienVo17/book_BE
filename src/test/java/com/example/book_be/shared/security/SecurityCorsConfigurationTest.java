package com.example.book_be.shared.security;

import com.example.book_be.shared.config.FrontendUrlProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityCorsConfigurationTest {

    @Test
    void allows_only_normalized_configured_frontend_origin() {
        SecurityConfiguration securityConfiguration = new SecurityConfiguration();
        CorsConfigurationSource source = securityConfiguration.corsConfigurationSource(
                new FrontendUrlProvider("https://frontend.example/"));
        CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest());

        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin("https://frontend.example")).isEqualTo("https://frontend.example");
        assertThat(configuration.checkOrigin("https://untrusted.example")).isNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly("https://frontend.example");
    }

    @Test
    void accepts_browser_origin_when_frontend_uses_explicit_default_port() {
        SecurityConfiguration securityConfiguration = new SecurityConfiguration();
        CorsConfiguration configuration = securityConfiguration.corsConfigurationSource(
                        new FrontendUrlProvider("https://frontend.example:443"))
                .getCorsConfiguration(new MockHttpServletRequest());

        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin("https://frontend.example"))
                .isEqualTo("https://frontend.example");
    }

    @Test
    void preserves_cors_methods_headers_credentials_and_max_age() {
        SecurityConfiguration securityConfiguration = new SecurityConfiguration();
        CorsConfiguration configuration = securityConfiguration.corsConfigurationSource(
                        new FrontendUrlProvider("https://frontend.example"))
                .getCorsConfiguration(new MockHttpServletRequest());

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedMethods())
                .containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(configuration.getAllowedHeaders()).containsExactly("*");
        assertThat(configuration.getAllowCredentials()).isTrue();
        assertThat(configuration.getMaxAge()).isEqualTo(3600L);
    }
}
