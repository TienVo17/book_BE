package com.example.book_be.portfolio;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.shared.web.ApiError;
import com.example.book_be.shared.web.RequestTraceFilter;
import com.example.book_be.nguoidung.domain.Quyen;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.repository.QuyenRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.http.HttpClient;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Malformed/wrong-signature/expired Bearer token phai fail closed (401/403 tat dinh), khong bao
 * gio la 500 khong xu ly. JWT hop le van giu nguyen hanh vi phan quyen hien tai.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class JwtSecurityBoundaryIT {

    private static final String FIXTURE_PREFIX = "jwt-boundary-";
    private static final String FIXTURE_PASSWORD = "JwtBoundary@123";
    private static final String PROTECTED_GET = "/api/dia-chi";

    @Autowired TestRestTemplate rest;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired QuyenRepository quyenRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager txManager;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private String fixtureRunId;
    private NguoiDung owner;

    @BeforeEach
    void provisionFixtures() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory(httpClient));
        fixtureRunId = FIXTURE_PREFIX + System.nanoTime();
        owner = taoNguoiDung("owner");
    }

    @AfterEach
    void cleanupFixtures() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            if (nguoiDungRepository.existsById((long) owner.getMaNguoiDung())) {
                nguoiDungRepository.deleteById((long) owner.getMaNguoiDung());
            }
        });
    }

    @Test
    void token_khong_dung_dinh_dang_fail_closed_khong_500() {
        ResponseEntity<ApiError> response = getWithBearer("khong-phai-jwt-hop-le", "jwt-trace-malformed");

        assertSecurityError(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "jwt-trace-malformed");
    }

    @Test
    void token_sai_chu_ky_fail_closed_khong_500() {
        Key wrongKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        String token = Jwts.builder()
                .setSubject(owner.getTenDangNhap())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(SignatureAlgorithm.HS256, wrongKey)
                .compact();

        ResponseEntity<ApiError> response = getWithBearer(token, "jwt-trace-invalid");

        assertSecurityError(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "jwt-trace-invalid");
    }

    @Test
    void token_het_han_fail_closed_khong_500() {
        String token = Jwts.builder()
                .setSubject(owner.getTenDangNhap())
                .setIssuedAt(new Date(System.currentTimeMillis() - 7_200_000))
                .setExpiration(new Date(System.currentTimeMillis() - 3_600_000))
                .signWith(SignatureAlgorithm.HS256, signingKey())
                .compact();

        ResponseEntity<ApiError> response = getWithBearer(token, "jwt-trace-invalid");

        assertSecurityError(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "jwt-trace-invalid");
    }

    @Test
    void token_hop_le_van_xac_thuc_binh_thuong() {
        String token = login(owner.getTenDangNhap());

        ResponseEntity<String> response = getWithBearer(token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> getWithBearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(PROTECTED_GET, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<ApiError> getWithBearer(String token, String traceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set(RequestTraceFilter.HEADER_NAME, traceId);
        return rest.exchange(PROTECTED_GET, HttpMethod.GET, new HttpEntity<>(headers), ApiError.class);
    }

    private void assertSecurityError(ResponseEntity<ApiError> response, HttpStatus status,
                                     String code, String traceId) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getFirst(RequestTraceFilter.HEADER_NAME)).isEqualTo(traceId);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().timestamp()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(status.value());
        assertThat(response.getBody().code()).isEqualTo(code);
        assertThat(response.getBody().message()).isNotBlank();
        assertThat(response.getBody().path()).isEqualTo(PROTECTED_GET);
        assertThat(response.getBody().traceId()).isEqualTo(traceId);
    }

    private Key signingKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private String login(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity(
                "/tai-khoan/dang-nhap",
                new HttpEntity<>("{\"username\":\"" + username + "\",\"password\":\"" + FIXTURE_PASSWORD + "\"}", headers),
                String.class);
        String content = response.getBody() == null ? "" : response.getBody();
        Matcher matcher = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+").matcher(content);
        String jwt = matcher.find() ? matcher.group() : "";
        assertThat(jwt).as("login succeeded for " + username).isNotBlank();
        return jwt;
    }

    /**
     * NguoiDung.danhSachQuyen cascades PERSIST, so a Quyen loaded outside this transaction
     * would arrive detached and fail the cascade. Load the role and save the user inside one
     * transaction so the role stays managed.
     */
    private NguoiDung taoNguoiDung(String suffix) {
        NguoiDung saved = new TransactionTemplate(txManager).execute(status -> {
            Quyen userRole = quyenRepository.findByTenQuyen("USER");
            assertThat(userRole).as("seed USER role exists").isNotNull();

            NguoiDung user = new NguoiDung();
            user.setHoDem("Jwt");
            user.setTen("Boundary");
            user.setTenDangNhap(fixtureRunId + "-" + suffix);
            user.setMatKhau(passwordEncoder.encode(FIXTURE_PASSWORD));
            user.setGioiTinh('X');
            user.setEmail(fixtureRunId + "-" + suffix + "@example.test");
            user.setSoDienThoai("0900000000");
            user.setDiaChiMuaHang("Jwt boundary fixture address");
            user.setDiaChiGiaoHang("Jwt boundary fixture delivery address");
            user.setDaKichHoat(true);
            user.setDanhSachQuyen(List.of(userRole));

            return nguoiDungRepository.saveAndFlush(user);
        });

        assertThat(saved).isNotNull();
        return saved;
    }
}
