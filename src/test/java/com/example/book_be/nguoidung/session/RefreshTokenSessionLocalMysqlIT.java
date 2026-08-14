package com.example.book_be.nguoidung.session;

import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.service.TaiKhoanService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=${AUTH_REFRESH_IT_DB_URL}",
                "spring.datasource.username=${AUTH_REFRESH_IT_DB_USERNAME}",
                "spring.datasource.password=${AUTH_REFRESH_IT_DB_PASSWORD:}",
                "jwt.secret=${JWT_SECRET}",
                "app.auth.refresh-enabled=true",
                "app.auth.refresh-hmac-key=${AUTH_REFRESH_HMAC_KEY}",
                "app.auth.csrf-hmac-key=${AUTH_CSRF_HMAC_KEY}",
                "app.frontend-url=https://tienvo17.vercel.app"
        })
@EnabledIfEnvironmentVariable(named = "AUTH_REFRESH_MYSQL_IT", matches = "true")
@AutoConfigureMockMvc
@Import(RefreshTokenSessionLocalMysqlIT.MailTestConfiguration.class)
class RefreshTokenSessionLocalMysqlIT {
    private static final String DEDICATED_SCHEMA = "web_ban_sach_auth_r1_test";
    private static final String DEDICATED_JDBC_URL =
            "jdbc:mysql://localhost:3306/" + DEDICATED_SCHEMA;
    private static final long TIMEOUT_SECONDS = 20;

    @DynamicPropertySource
    static void configureDedicatedMysqlSession(DynamicPropertyRegistry registry) {
        String configuredUrl = System.getenv("AUTH_REFRESH_IT_DB_URL");
        if (!DEDICATED_JDBC_URL.equals(configuredUrl)) {
            throw new IllegalStateException(
                    "AUTH_REFRESH_IT_DB_URL must target the dedicated Release 1 test schema");
        }
        registry.add("spring.datasource.url", () -> configuredUrl
                + "?sessionVariables=sql_require_primary_key=1");
    }

    @Autowired RefreshSessionService refreshSessionService;
    @Autowired RefreshTokenCodec refreshTokenCodec;
    @Autowired RefreshTokenSessionRepository refreshRepository;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired TaiKhoanService taiKhoanService;
    @Autowired com.example.book_be.nguoidung.baomat.JwtService jwtService;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired MockMvc mockMvc;

    private NguoiDung user;

    @BeforeEach
    void validateDedicatedSchemaAndCreateFixture() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .as("integration test must never target the application database")
                .isEqualTo(DEDICATED_SCHEMA);
        assertThat(jdbcTemplate.queryForObject("SELECT VERSION()", String.class))
                .startsWith("8.0.");

        user = new TransactionTemplate(transactionManager).execute(status -> {
            String runId = "refresh-r1-it-" + System.nanoTime();
            NguoiDung fixture = new NguoiDung();
            fixture.setHoDem("Refresh");
            fixture.setTen("Session IT");
            fixture.setTenDangNhap(runId);
            fixture.setMatKhau(passwordEncoder.encode("old-password"));
            fixture.setGioiTinh('X');
            fixture.setEmail(runId + "@example.test");
            fixture.setDaKichHoat(true);
            return nguoiDungRepository.saveAndFlush(fixture);
        });
        assertThat(user).isNotNull();
    }

    @AfterEach
    void deleteExactFixture() {
        if (user == null) {
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("DELETE FROM refresh_token_session WHERE ma_nguoi_dung = ?",
                    user.getMaNguoiDung());
            nguoiDungRepository.deleteById((long) user.getMaNguoiDung());
            nguoiDungRepository.flush();
        });
    }

    @Test
    void retired_proxy_rehearsal_prefix_is_safe_not_found_for_every_request_shape()
            throws Exception {
        assertRetiredProbeNotFound(get("/tai-khoan/_proxy-rehearsal"),
                "/tai-khoan/_proxy-rehearsal", "retired-probe-base");
        assertRetiredProbeNotFound(get("/tai-khoan/_proxy-rehearsal/issue"),
                "/tai-khoan/_proxy-rehearsal/issue", "retired-probe-issue");
        assertRetiredProbeNotFound(get("/tai-khoan/_proxy-rehearsal/redirect"),
                "/tai-khoan/_proxy-rehearsal/redirect", "retired-probe-redirect");
        assertRetiredProbeNotFound(get("/tai-khoan/_proxy-rehearsal/complete"),
                "/tai-khoan/_proxy-rehearsal/complete", "retired-probe-complete");
        assertRetiredProbeNotFound(post("/tai-khoan/_proxy-rehearsal/arbitrary")
                        .contentType("application/json")
                        .content("{\"secret\":\"must-not-be-reflected\"}")
                        .header("Authorization", "Bearer malformed"),
                "/tai-khoan/_proxy-rehearsal/arbitrary", "retired-probe-post");
        assertRetiredProbeNotFound(delete("/tai-khoan/_proxy-rehearsal/arbitrary/deep"),
                "/tai-khoan/_proxy-rehearsal/arbitrary/deep", "retired-probe-delete");
        assertRetiredProbeNotFound(options("/tai-khoan/_proxy-rehearsal/issue")
                        .header("Origin", "https://tienvo17.vercel.app")
                        .header("Access-Control-Request-Method", "POST"),
                "/tai-khoan/_proxy-rehearsal/issue", "retired-probe-preflight");
    }

    private void assertRetiredProbeNotFound(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String path,
            String traceId) throws Exception {
        mockMvc.perform(request.header("X-Trace-Id", traceId))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("CDN-Cache-Control", "no-store"))
                .andExpect(header().string("X-Trace-Id", traceId))
                .andExpect(header().string("Set-Cookie", nullValue()))
                .andExpect(header().string("Location", nullValue()))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value(path))
                .andExpect(jsonPath("$.traceId").value(traceId))
                .andExpect(jsonPath("$.secret").doesNotExist());
    }

    @Test
    void refresh_origin_rejection_uses_common_no_store_envelope_before_cors() throws Exception {
        mockMvc.perform(post("/tai-khoan/refresh")
                        .header("Origin", "https://untrusted.example")
                        .header("X-Trace-Id", "auth-origin-rejected"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("CDN-Cache-Control", "no-store"))
                .andExpect(header().string("X-Trace-Id", "auth-origin-rejected"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("AUTH_ORIGIN_REJECTED"))
                .andExpect(jsonPath("$.path").value("/tai-khoan/refresh"))
                .andExpect(jsonPath("$.traceId").value("auth-origin-rejected"));
    }

    @Test
    void valid_refresh_preflight_reaches_cors_without_requiring_csrf() throws Exception {
        mockMvc.perform(options("/tai-khoan/refresh")
                        .header("Origin", "https://tienvo17.vercel.app")
                        .header("Access-Control-Request-Method", "POST")
                        .header("X-Trace-Id", "auth-preflight"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin",
                        "https://tienvo17.vercel.app"))
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("CDN-Cache-Control", "no-store"))
                .andExpect(header().string("X-Trace-Id", "auth-preflight"));
    }

    @Test
    void v21_schema_and_atomic_rotation_reuse_contract_hold_on_local_mysql() throws Exception {
        assertV21SchemaContract();
        RefreshSessionService.SessionGrant initial = refreshSessionService.issue(user, true);
        String originalSelector = refreshTokenCodec.selectorOf(initial.rawToken());
        String familyId = jdbcTemplate.queryForObject(
                "SELECT family_id FROM refresh_token_session WHERE selector = ?",
                String.class, originalSelector);

        List<RotationOutcome> outcomes = rotateConcurrently(initial.rawToken());

        assertThat(outcomes).filteredOn(outcome -> outcome.grant() != null).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> outcome.error() != null)
                .singleElement()
                .satisfies(outcome -> assertThat(outcome.error())
                        .isInstanceOf(RefreshSessionException.class)
                        .extracting(Throwable::getMessage)
                        .isEqualTo("REAUTHENTICATION_REQUIRED"));

        RefreshSessionService.SessionGrant child = outcomes.stream()
                .map(RotationOutcome::grant)
                .filter(grant -> grant != null)
                .findFirst()
                .orElseThrow();
        String childSelector = refreshTokenCodec.selectorOf(child.rawToken());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token_session WHERE family_id = ?",
                Integer.class, familyId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token_session "
                        + "WHERE family_id = ? AND revoked_at IS NULL",
                Integer.class, familyId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token_session "
                        + "WHERE selector = ? AND revoked_at IS NOT NULL",
                Integer.class, childSelector)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT replaced_by_selector FROM refresh_token_session WHERE selector = ?",
                String.class, originalSelector)).isEqualTo(childSelector);

        assertThatThrownBy(() -> refreshSessionService.rotate(initial.rawToken()))
                .isInstanceOf(RefreshSessionException.class)
                .extracting(Throwable::getMessage)
                .isEqualTo("REAUTHENTICATION_REQUIRED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token_session "
                        + "WHERE family_id = ? AND revoked_at IS NULL",
                Integer.class, familyId)).isZero();
    }

    @Test
    void password_change_serializes_with_login_session_issuance() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<LoginOutcome> login = pool.submit(() -> {
                ready.countDown();
                if (!start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Login race start barrier timed out");
                }
                try {
                    return new LoginOutcome(taiKhoanService.authenticateAndIssueSession(
                            user.getTenDangNhap(), "old-password", true), null);
                } catch (RuntimeException exception) {
                    return new LoginOutcome(null, exception);
                }
            });
            Future<PasswordChangeOutcome> passwordChange = pool.submit(() -> {
                ready.countDown();
                if (!start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Password change race start barrier timed out");
                }
                try {
                    taiKhoanService.doiMatKhau(
                            user.getTenDangNhap(), "old-password", "new-password");
                    return new PasswordChangeOutcome(true, null);
                } catch (RuntimeException exception) {
                    return new PasswordChangeOutcome(false, exception);
                }
            });

            assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            LoginOutcome loginOutcome = login.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            PasswordChangeOutcome passwordChangeOutcome =
                    passwordChange.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            String persistedPassword = jdbcTemplate.queryForObject(
                    "SELECT mat_khau FROM nguoi_dung WHERE ma_nguoi_dung = ?",
                    String.class, user.getMaNguoiDung());
            assertThat(passwordChangeOutcome.succeeded()).isTrue();
            assertThat(passwordChangeOutcome.error()).isNull();
            assertThat(passwordEncoder.matches("new-password", persistedPassword)).isTrue();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM refresh_token_session "
                            + "WHERE ma_nguoi_dung = ? AND revoked_at IS NULL",
                    Integer.class, user.getMaNguoiDung())).isZero();
            assertThat(loginOutcome.grant() == null).isEqualTo(loginOutcome.error() != null);
            if (loginOutcome.grant() != null) {
                assertThat(loginOutcome.grant().accessToken()).isNotBlank();
                assertThat(jwtService.extractUsername(loginOutcome.grant().accessToken()))
                        .isEqualTo(user.getTenDangNhap());
            }
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void reset_token_is_consumed_once_under_concurrency() throws Exception {
        String resetToken = "one-time-reset-token";
        jdbcTemplate.update(
                "UPDATE nguoi_dung SET reset_password_token = ?, "
                        + "reset_password_token_expiry = ? "
                        + "WHERE ma_nguoi_dung = ?",
                resetToken,
                new java.sql.Timestamp(System.currentTimeMillis() + 600_000L),
                user.getMaNguoiDung());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ResetOutcome> first = pool.submit(() -> resetAfterBarrier(
                    resetToken, "new-password-one", ready, start));
            Future<ResetOutcome> second = pool.submit(() -> resetAfterBarrier(
                    resetToken, "new-password-two", ready, start));
            assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ResetOutcome> outcomes = List.of(
                    first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertThat(outcomes)
                    .as("concurrent reset outcomes: %s", outcomes)
                    .filteredOn(ResetOutcome::succeeded)
                    .hasSize(1);
            assertThat(outcomes).filteredOn(outcome -> outcome.error() != null)
                    .singleElement();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM nguoi_dung WHERE ma_nguoi_dung = ? "
                            + "AND reset_password_token IS NULL "
                            + "AND reset_password_token_expiry IS NULL",
                    Integer.class, user.getMaNguoiDung())).isOne();
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
    }

    private ResetOutcome resetAfterBarrier(String token, String newPassword,
                                            CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Reset race start barrier timed out");
        }
        try {
            taiKhoanService.datLaiMatKhau(user.getEmail(), token, newPassword);
            return new ResetOutcome(true, null);
        } catch (RuntimeException exception) {
            return new ResetOutcome(false, exception);
        }
    }

    private List<RotationOutcome> rotateConcurrently(String rawToken) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<RotationOutcome> first = pool.submit(() -> rotateAfterBarrier(rawToken, ready, start));
            Future<RotationOutcome> second = pool.submit(() -> rotateAfterBarrier(rawToken, ready, start));
            assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(
                    first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
    }

    private RotationOutcome rotateAfterBarrier(String rawToken, CountDownLatch ready,
                                                CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent rotation start barrier timed out");
        }
        try {
            return new RotationOutcome(refreshSessionService.rotate(rawToken), null);
        } catch (RuntimeException exception) {
            return new RotationOutcome(null, exception);
        }
    }

    private void assertV21SchemaContract() {
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
                "SELECT INDEX_NAME, NON_UNIQUE, "
                        + "GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS COLUMNS_IN_INDEX "
                        + "FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refresh_token_session' "
                        + "GROUP BY INDEX_NAME, NON_UNIQUE");
        assertThat(indexes).anySatisfy(index -> assertIndex(index, "PRIMARY", 0, "id"));
        assertThat(indexes).anySatisfy(index -> assertIndex(
                index, "uk_refresh_token_session_selector", 0, "selector"));
        assertThat(indexes).anySatisfy(index -> assertIndex(
                index, "idx_refresh_token_session_user_revoked", 1,
                "ma_nguoi_dung,revoked_at"));
        assertThat(indexes).anySatisfy(index -> assertIndex(
                index, "idx_refresh_token_session_family_revoked", 1,
                "family_id,revoked_at"));
        assertThat(indexes).anySatisfy(index -> assertIndex(
                index, "idx_refresh_token_session_absolute_expiry", 1,
                "absolute_expires_at"));

        Map<String, Object> foreignKey = jdbcTemplate.queryForMap(
                "SELECT CONSTRAINT_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, "
                        + "REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE "
                        + "WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = 'refresh_token_session' "
                        + "AND CONSTRAINT_NAME = 'fk_refresh_token_session_nguoi_dung'");
        assertThat(foreignKey)
                .containsEntry("COLUMN_NAME", "ma_nguoi_dung")
                .containsEntry("REFERENCED_TABLE_NAME", "nguoi_dung")
                .containsEntry("REFERENCED_COLUMN_NAME", "ma_nguoi_dung");
    }

    private void assertIndex(Map<String, Object> index, String name, int nonUnique,
                             String columns) {
        assertThat(index.get("INDEX_NAME")).isEqualTo(name);
        assertThat(((Number) index.get("NON_UNIQUE")).intValue()).isEqualTo(nonUnique);
        assertThat(index.get("COLUMNS_IN_INDEX")).isEqualTo(columns);
    }

    private record RotationOutcome(RefreshSessionService.SessionGrant grant,
                                   RuntimeException error) {
    }

    private record LoginOutcome(TaiKhoanService.AuthenticatedSession grant,
                                RuntimeException error) {
    }

    private record PasswordChangeOutcome(boolean succeeded, RuntimeException error) {
    }

    private record ResetOutcome(boolean succeeded, RuntimeException error) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MailTestConfiguration {
        @Bean
        JavaMailSender javaMailSender() {
            return new JavaMailSender() {
                @Override
                public MimeMessage createMimeMessage() {
                    return new MimeMessage(Session.getInstance(new java.util.Properties()));
                }

                @Override
                public MimeMessage createMimeMessage(java.io.InputStream contentStream) {
                    throw new UnsupportedOperationException("Mail parsing is not used by this integration test");
                }

                @Override
                public void send(MimeMessage mimeMessage) {
                    throw new UnsupportedOperationException("Mail sending is not used by this integration test");
                }

                @Override
                public void send(MimeMessage... mimeMessages) {
                    throw new UnsupportedOperationException("Mail sending is not used by this integration test");
                }

                @Override
                public void send(org.springframework.mail.SimpleMailMessage simpleMessage) {
                    throw new UnsupportedOperationException("Mail sending is not used by this integration test");
                }

                @Override
                public void send(org.springframework.mail.SimpleMailMessage... simpleMessages) {
                    throw new UnsupportedOperationException("Mail sending is not used by this integration test");
                }
            };
        }
    }
}
