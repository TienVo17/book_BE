package com.example.book_be.shared.web;

import com.example.book_be.shared.config.FrontendUrlProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProxyRehearsalControllerTest {
    private static final String ISSUE_PATH = "/tai-khoan/_proxy-rehearsal/issue";
    private static final String REDIRECT_PATH = "/tai-khoan/_proxy-rehearsal/redirect";
    private static final String COMPLETE_PATH = "/tai-khoan/_proxy-rehearsal/complete";
    private static final String DUMMY_AUTHORIZATION = "Bearer proxy-rehearsal-dummy";

    @Test
    void returns_not_found_when_rehearsal_is_disabled() throws Exception {
        mockMvc(false).perform(post(ISSUE_PATH)
                        .header("Origin", "https://tienvo17.vercel.app")
                        .header("Authorization", DUMMY_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"probe\":\"proxy-rehearsal\"}"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("CDN-Cache-Control", "no-store"))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.traceId").isString());
    }

    @Test
    void issue_accepts_only_canonical_origin_and_returns_safe_cookie_contract() throws Exception {
        String rawCookie = "proxy-cookie-value";

        mockMvc(true).perform(post(ISSUE_PATH)
                        .header("Origin", "https://tienvo17.vercel.app")
                        .header("Authorization", DUMMY_AUTHORIZATION)
                        .header("Cookie", "unrelated=" + rawCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"probe\":\"proxy-rehearsal\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("CDN-Cache-Control", "no-store"))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(header().string("Set-Cookie", containsString("__Host-proxy-rehearsal=")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/")))
                .andExpect(header().string("Set-Cookie", containsString("Secure")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
                .andExpect(header().string("Set-Cookie", not(containsString("Domain="))))
                .andExpect(jsonPath("$.authorizationAccepted").value(true))
                .andExpect(jsonPath("$.bodyAccepted").value(true))
                .andExpect(jsonPath("$.cookieIssued").value(true))
                .andExpect(jsonPath("$.requestDigest").isString())
                .andExpect(jsonPath("$.traceId").isString())
                .andExpect(jsonPath("$.authorization").doesNotExist())
                .andExpect(jsonPath("$.cookie").doesNotExist())
                .andExpect(jsonPath("$.probe").doesNotExist())
                .andExpect(content().string(not(containsString(rawCookie))))
                .andExpect(content().string(not(containsString(DUMMY_AUTHORIZATION))));
    }

    @Test
    void rejects_noncanonical_or_normalized_origin_fail_closed() throws Exception {
        mockMvc(true).perform(post(ISSUE_PATH)
                        .header("Origin", "https://tienvo17.vercel.app/")
                        .header("Authorization", DUMMY_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"probe\":\"proxy-rehearsal\"}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.code").value("PROXY_REHEARSAL_ORIGIN_REJECTED"))
                .andExpect(jsonPath("$.traceId").isString());

        mockMvc(true).perform(post(ISSUE_PATH)
                        .header("Origin", "https://untrusted.example")
                        .header("Authorization", DUMMY_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"probe\":\"proxy-rehearsal\"}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(jsonPath("$.code").value("PROXY_REHEARSAL_ORIGIN_REJECTED"));
    }

    @Test
    void redirect_and_complete_require_cookie_and_never_expose_it() throws Exception {
        mockMvc(true).perform(get(REDIRECT_PATH))
                .andExpect(status().isBadRequest())
                .andExpect(noStoreHeaders())
                .andExpect(jsonPath("$.code").value("PROXY_REHEARSAL_COOKIE_REQUIRED"))
                .andExpect(jsonPath("$.traceId").isString());

        mockMvc(true).perform(get(REDIRECT_PATH)
                        .cookie(new jakarta.servlet.http.Cookie("__Host-proxy-rehearsal", "opaque-cookie-value")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", COMPLETE_PATH))
                .andExpect(noStoreHeaders());

        mockMvc(true).perform(get(COMPLETE_PATH)
                        .cookie(new jakarta.servlet.http.Cookie("__Host-proxy-rehearsal", "opaque-cookie-value")))
                .andExpect(status().isOk())
                .andExpect(noStoreHeaders())
                .andExpect(header().string("Set-Cookie", containsString("__Host-proxy-rehearsal=")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/")))
                .andExpect(header().string("Set-Cookie", containsString("Secure")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
                .andExpect(jsonPath("$.cookieSeen").value(true))
                .andExpect(content().string(not(containsString("opaque-cookie-value"))));
    }

    private org.springframework.test.web.servlet.ResultMatcher noStoreHeaders() {
        return result -> {
            header().string("Cache-Control", "no-store, private").match(result);
            header().string("Pragma", "no-cache").match(result);
            header().string("CDN-Cache-Control", "no-store").match(result);
        };
    }

    private MockMvc mockMvc(boolean enabled) {
        FrontendUrlProvider frontendUrlProvider =
                new FrontendUrlProvider("https://tienvo17.vercel.app");
        return MockMvcBuilders.standaloneSetup(
                        new ProxyRehearsalController(enabled, frontendUrlProvider))
                .addFilters(
                        new RequestTraceFilter(),
                        new ProxyRehearsalNoStoreFilter(frontendUrlProvider))
                .build();
    }
}
