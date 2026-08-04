package com.example.book_be.shared.web;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.domain.Quyen;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.repository.QuyenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class ApiErrorContractIT {
    private static final String PASSWORD = "ApiError@123";

    @Autowired TestRestTemplate rest;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired QuyenRepository quyenRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager txManager;

    private NguoiDung user;
    private String token;

    @BeforeEach
    void provisionUser() {
        user = new TransactionTemplate(txManager).execute(status -> {
            Quyen role = quyenRepository.findByTenQuyen("USER");
            NguoiDung entity = new NguoiDung();
            entity.setHoDem("Api");
            entity.setTen("Error");
            entity.setTenDangNhap("api-error-" + System.nanoTime());
            entity.setMatKhau(passwordEncoder.encode(PASSWORD));
            entity.setGioiTinh('X');
            entity.setEmail(entity.getTenDangNhap() + "@example.test");
            entity.setSoDienThoai("0900000000");
            entity.setDaKichHoat(true);
            entity.setDanhSachQuyen(List.of(role));
            return nguoiDungRepository.saveAndFlush(entity);
        });
        token = login(user.getTenDangNhap());
    }

    @AfterEach
    void cleanup() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            if (user != null && nguoiDungRepository.existsById((long) user.getMaNguoiDung())) {
                nguoiDungRepository.deleteById((long) user.getMaNguoiDung());
            }
        });
    }

    @Test
    void security_401_uses_common_schema_and_same_trace_header() {
        assertError(exchange("/api/dia-chi", HttpMethod.GET, null, null, "trace-401"),
                HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "/api/dia-chi", "trace-401");
    }

    @Test
    void security_403_uses_common_schema_and_same_trace_header() {
        assertError(exchange("/api/admin/thong-ke", HttpMethod.GET, null, token, "trace-403"),
                HttpStatus.FORBIDDEN, "FORBIDDEN", "/api/admin/thong-ke", "trace-403");
    }

    @Test
    void malformed_json_uses_common_validation_schema() {
        assertError(exchange("/api/don-hang/them", HttpMethod.POST, "{broken", token, "trace-json"),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "/api/don-hang/them", "trace-json");
    }

    @Test
    void response_status_exception_uses_common_schema() {
        assertError(exchange("/api/don-hang/999999999", HttpMethod.GET, null, token, "trace-not-found"),
                HttpStatus.NOT_FOUND, "NOT_FOUND", "/api/don-hang/999999999", "trace-not-found");
    }

    @Test
    void review_with_missing_book_is_a_traced_404_not_internal_error() {
        String body = "{\"maSach\":999999999,\"nhanXet\":\"test\",\"diemXepHang\":5}";
        assertError(exchange("/api/danh-gia/them-danh-gia-v1", HttpMethod.POST,
                        body, token, "trace-review-not-found"),
                HttpStatus.NOT_FOUND, "NOT_FOUND",
                "/api/danh-gia/them-danh-gia-v1", "trace-review-not-found");
    }

    @Test
    void rate_limited_login_is_coded_as_rate_limited_not_internal_error() {
        String body = "{\"username\":\"" + user.getTenDangNhap() + "\",\"password\":\"sai-mat-khau\"}";
        // Six consecutive failures: the sixth crosses the configured lockout.
        for (int attempt = 0; attempt < 5; attempt++) {
            exchange("/tai-khoan/dang-nhap", HttpMethod.POST, body, null, "trace-rate-warm");
        }

        assertError(exchange("/tai-khoan/dang-nhap", HttpMethod.POST, body, null, "trace-rate-limited"),
                HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "/tai-khoan/dang-nhap", "trace-rate-limited");
    }

    @Test
    void review_missing_required_fields_is_a_traced_400_not_internal_error() {
        for (String body : List.of(
                "{\"nhanXet\":\"test\",\"diemXepHang\":5}",
                "{\"maSach\":1,\"nhanXet\":\"test\"}",
                "{\"maSach\":1,\"nhanXet\":\"\",\"diemXepHang\":5}",
                "{\"maSach\":1,\"nhanXet\":\"test\",\"diemXepHang\":6}")) {
            ResponseEntity<ApiError> response = exchange(
                    "/api/danh-gia/them-danh-gia-v1", HttpMethod.POST,
                    body, token, "trace-review-validation");
            assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "/api/danh-gia/them-danh-gia-v1", "trace-review-validation");
        }
    }

    private ResponseEntity<ApiError> exchange(String path, HttpMethod method, String body,
                                               String jwt, String traceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(RequestTraceFilter.HEADER_NAME, traceId);
        if (jwt != null) {
            headers.setBearerAuth(jwt);
        }
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), ApiError.class);
    }

    private void assertError(ResponseEntity<ApiError> response, HttpStatus status,
                             String code, String path, String traceId) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getFirst(RequestTraceFilter.HEADER_NAME)).isEqualTo(traceId);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().timestamp()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(status.value());
        assertThat(response.getBody().code()).isEqualTo(code);
        assertThat(response.getBody().message()).isNotBlank();
        assertThat(response.getBody().path()).isEqualTo(path);
        assertThat(response.getBody().traceId()).isEqualTo(traceId);
    }

    private String login(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity(
                "/tai-khoan/dang-nhap",
                new HttpEntity<>("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}", headers),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotBlank();
        String body = response.getBody();
        int start = body.indexOf("\"jwt\":\"") + 7;
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }
}
