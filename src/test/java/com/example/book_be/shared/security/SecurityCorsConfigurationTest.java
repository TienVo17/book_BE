package com.example.book_be.shared.security;

import com.example.book_be.shared.config.FrontendUrlProvider;
import com.example.book_be.shared.web.ProxyRehearsalController;
import com.example.book_be.shared.web.ProxyRehearsalNoStoreFilter;
import com.example.book_be.shared.web.RequestTraceFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    void applies_no_store_before_cors_rejects_a_disallowed_rehearsal_origin() throws Exception {
        SecurityConfiguration securityConfiguration = new SecurityConfiguration();
        CorsConfigurationSource corsSource = securityConfiguration.corsConfigurationSource(
                new FrontendUrlProvider("https://tienvo17.vercel.app"));
        FrontendUrlProvider frontendUrlProvider =
                new FrontendUrlProvider("https://tienvo17.vercel.app");
        FilterRegistrationBean<RequestTraceFilter> traceRegistration =
                securityConfiguration.requestTraceFilterRegistration(new RequestTraceFilter());
        FilterRegistrationBean<ProxyRehearsalNoStoreFilter> registration =
                securityConfiguration.proxyRehearsalNoStoreFilterRegistration(frontendUrlProvider);
        assertThat(traceRegistration.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
        assertThat(registration.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 1);
        assertThat(registration.getUrlPatterns()).containsExactlyInAnyOrder(
                ProxyRehearsalController.ISSUE_PATH,
                ProxyRehearsalController.REDIRECT_PATH,
                ProxyRehearsalController.COMPLETE_PATH
        );

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new ProxyRehearsalController(true, frontendUrlProvider))
                .addFilters(
                        traceRegistration.getFilter(),
                        registration.getFilter(),
                        new CorsFilter(corsSource)
                )
                .build();

        mockMvc.perform(options(ProxyRehearsalController.ISSUE_PATH)
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("CDN-Cache-Control", "no-store"))
                .andExpect(header().exists(RequestTraceFilter.HEADER_NAME))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentType("application/json;charset=UTF-8"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.code").value("PROXY_REHEARSAL_ORIGIN_REJECTED"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.traceId").isString());
    }

    @Test
    void permits_only_the_three_exact_proxy_rehearsal_routes() {
        assertThat(Endpoints.PROXY_REHEARSAL_GET_ENDPOINTS).containsExactly(
                "/tai-khoan/_proxy-rehearsal/redirect",
                "/tai-khoan/_proxy-rehearsal/complete"
        );
        assertThat(Endpoints.PROXY_REHEARSAL_POST_ENDPOINTS).containsExactly(
                "/tai-khoan/_proxy-rehearsal/issue"
        );
        assertThat(Endpoints.PROXY_REHEARSAL_GET_ENDPOINTS)
                .allMatch(endpoint -> !endpoint.contains("**"));
        assertThat(Endpoints.PROXY_REHEARSAL_POST_ENDPOINTS)
                .allMatch(endpoint -> !endpoint.contains("**"));
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
        assertThat(configuration.getExposedHeaders()).containsExactly("X-Trace-Id");
        assertThat(configuration.getAllowCredentials()).isTrue();
        assertThat(configuration.getMaxAge()).isEqualTo(3600L);
    }
}
