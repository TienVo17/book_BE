package com.example.book_be.portfolio;

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
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.http.HttpClient;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Enumerates every {@code /api/admin/**} method/path and every exported Spring Data REST
 * repository. Anonymous and normal USER callers must be denied everywhere in the admin
 * namespace (including the former public image read); sensitive repositories must not expose
 * collection/item reads; the two public account-existence search endpoints and the read-only
 * catalog relation the frontend uses must keep working.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class AdminAndRepositoryExposureIT {

    private static final String FIXTURE_PREFIX = "admin-exposure-";
    private static final String FIXTURE_PASSWORD = "AdminExposure@123";

    @Autowired TestRestTemplate rest;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired QuyenRepository quyenRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager txManager;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private String fixtureRunId;
    private NguoiDung normalUser;
    private NguoiDung adminUser;
    private String jwtUser;
    private String jwtAdmin;

    @BeforeEach
    void provisionFixtures() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory(httpClient));
        fixtureRunId = FIXTURE_PREFIX + System.nanoTime();
        normalUser = taoNguoiDung("user", "USER");
        adminUser = taoNguoiDung("admin", "ADMIN");
        jwtUser = login(normalUser.getTenDangNhap());
        jwtAdmin = login(adminUser.getTenDangNhap());
    }

    @AfterEach
    void cleanupFixtures() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            if (nguoiDungRepository.existsById((long) normalUser.getMaNguoiDung())) {
                nguoiDungRepository.deleteById((long) normalUser.getMaNguoiDung());
            }
            if (nguoiDungRepository.existsById((long) adminUser.getMaNguoiDung())) {
                nguoiDungRepository.deleteById((long) adminUser.getMaNguoiDung());
            }
        });
    }

    static Stream<Arguments> adminEndpoints() {
        return Stream.of(
                arguments(HttpMethod.GET, "/api/admin/sach?page=0"),
                arguments(HttpMethod.GET, "/api/admin/sach/1"),
                arguments(HttpMethod.GET, "/api/admin/sach/findImage/1"),
                arguments(HttpMethod.POST, "/api/admin/sach/insert"),
                arguments(HttpMethod.POST, "/api/admin/sach/active/1"),
                arguments(HttpMethod.POST, "/api/admin/sach/unactive/1"),
                arguments(HttpMethod.POST, "/api/admin/sach/migrate-hinh-anh-base64"),
                arguments(HttpMethod.PUT, "/api/admin/sach/update/1"),
                arguments(HttpMethod.PATCH, "/api/admin/sach/1/ton-kho"),
                arguments(HttpMethod.DELETE, "/api/admin/sach/delete/1"),
                arguments(HttpMethod.GET, "/api/admin/danh-gia/findAll?page=0"),
                arguments(HttpMethod.POST, "/api/admin/danh-gia/active/1"),
                arguments(HttpMethod.POST, "/api/admin/danh-gia/unactive/1"),
                arguments(HttpMethod.GET, "/api/admin/coupon"),
                arguments(HttpMethod.POST, "/api/admin/coupon"),
                arguments(HttpMethod.PUT, "/api/admin/coupon/1"),
                arguments(HttpMethod.DELETE, "/api/admin/coupon/1"),
                arguments(HttpMethod.GET, "/api/admin/the-loai"),
                arguments(HttpMethod.POST, "/api/admin/the-loai"),
                arguments(HttpMethod.PUT, "/api/admin/the-loai/1"),
                arguments(HttpMethod.DELETE, "/api/admin/the-loai/1"),
                arguments(HttpMethod.GET, "/api/admin/thong-ke"),
                arguments(HttpMethod.GET, "/api/admin/don-hang/findAll?page=0"),
                arguments(HttpMethod.GET, "/api/admin/quyen/findAll"),
                arguments(HttpMethod.GET, "/api/admin/user?page=0"),
                arguments(HttpMethod.POST, "/api/admin/user/phan-quyen")
        );
    }

    @ParameterizedTest(name = "{0} {1} an danh bi tu choi")
    @MethodSource("adminEndpoints")
    void moi_endpoint_admin_tu_choi_khach_an_danh(HttpMethod method, String path) {
        ResponseEntity<String> response = call(method, path, new HttpHeaders());

        assertThat(response.getStatusCode().value())
                .as(method + " " + path + " (an danh)")
                .isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
    }

    @ParameterizedTest(name = "{0} {1} tu choi USER thuong")
    @MethodSource("adminEndpoints")
    void moi_endpoint_admin_tu_choi_user_thuong(HttpMethod method, String path) {
        ResponseEntity<String> response = call(method, path, bearerHeaders(jwtUser));

        assertThat(response.getStatusCode()).as(method + " " + path + " (user)").isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void admin_that_su_van_truy_cap_duoc_thong_ke() {
        ResponseEntity<String> response = rest.exchange(
                "/api/admin/thong-ke", HttpMethod.GET, new HttpEntity<>(bearerHeaders(jwtAdmin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    static Stream<Arguments> sensitiveRepositoryCollections() {
        return Stream.of(
                arguments("/nguoi-dung"),
                arguments("/don-hang"),
                arguments("/chi-tiet-don-hang"),
                arguments("/sach-yeu-thich"),
                arguments("/su-danh-gia"),
                arguments("/gio-hang"),
                arguments("/quyen")
        );
    }

    @ParameterizedTest(name = "GET {0} dong voi khach an danh")
    @MethodSource("sensitiveRepositoryCollections")
    void repository_nhay_cam_dong_voi_khach_an_danh(String path) {
        ResponseEntity<String> collection = rest.getForEntity(path, String.class);
        ResponseEntity<String> item = rest.getForEntity(path + "/1", String.class);

        assertThat(collection.getStatusCode().is2xxSuccessful()).as("collection " + path).isFalse();
        assertThat(item.getStatusCode().is2xxSuccessful()).as("item " + path + "/1").isFalse();
    }

    @ParameterizedTest(name = "GET {0} dong voi USER thuong")
    @MethodSource("sensitiveRepositoryCollections")
    void repository_nhay_cam_dong_voi_user_thuong(String path) {
        ResponseEntity<String> collection = rest.exchange(
                path, HttpMethod.GET, new HttpEntity<>(bearerHeaders(jwtUser)), String.class);
        ResponseEntity<String> item = rest.exchange(
                path + "/1", HttpMethod.GET, new HttpEntity<>(bearerHeaders(jwtUser)), String.class);

        assertThat(collection.getStatusCode().is2xxSuccessful()).as("collection " + path).isFalse();
        assertThat(item.getStatusCode().is2xxSuccessful()).as("item " + path + "/1").isFalse();
    }

    @Test
    void tim_kiem_ton_tai_ten_dang_nhap_van_cong_khai() {
        ResponseEntity<String> response = rest.getForEntity(
                "/nguoi-dung/search/existsByTenDangNhap?tenDangNhap=" + normalUser.getTenDangNhap(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void tim_kiem_ton_tai_email_van_cong_khai() {
        ResponseEntity<String> response = rest.getForEntity(
                "/nguoi-dung/search/existsByEmail?email=" + normalUser.getEmail(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Quyet dinh nguoc lai voi ban truoc cua test nay, va co chu y.
     *
     * <p>Truoc day test ten {@code quan_he_danh_gia_cua_sach_van_doc_duoc_cong_khai} khang dinh
     * endpoint nay tra HTTP 200 cho khach an danh. Bang chung moi ngay 2026-08-05: no tra ve ca
     * danh gia da bi admin an, kem nguyen co trang thai cu, nen thao tac kiem duyet khong
     * co tac dung that. Vi vay association nay bi dong lai
     * ({@code @RepositoryRestResource(exported = false)} tren SuDanhGiaRepository).
     *
     * <p>Danh gia cong khai gio doc qua {@code /api/danh-gia}, noi co bo loc trang thai.
     */
    @Test
    void quan_he_danh_gia_cua_sach_khong_con_doc_duoc_cong_khai() {
        ResponseEntity<String> response = rest.getForEntity("/sach/1/listDanhGia", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("association listDanhGia da bi dong de kiem duyet co hieu luc")
                .isFalse();
    }

    private ResponseEntity<String> call(HttpMethod method, String path, HttpHeaders callerHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(callerHeaders);
        Object body = "";
        if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH) {
            headers.setContentType(MediaType.APPLICATION_JSON);
            body = "{}";
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private HttpHeaders bearerHeaders(String jwt) {
        assertThat(jwt).isNotBlank();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        return headers;
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
    private NguoiDung taoNguoiDung(String suffix, String tenQuyen) {
        NguoiDung saved = new TransactionTemplate(txManager).execute(status -> {
            Quyen role = quyenRepository.findByTenQuyen(tenQuyen);
            assertThat(role).as("seed " + tenQuyen + " role exists").isNotNull();

            NguoiDung user = new NguoiDung();
            user.setHoDem("Admin");
            user.setTen("Exposure");
            user.setTenDangNhap(fixtureRunId + "-" + suffix);
            user.setMatKhau(passwordEncoder.encode(FIXTURE_PASSWORD));
            user.setGioiTinh('X');
            user.setEmail(fixtureRunId + "-" + suffix + "@example.test");
            user.setSoDienThoai("0900000000");
            user.setDiaChiMuaHang("Admin exposure fixture address");
            user.setDiaChiGiaoHang("Admin exposure fixture delivery address");
            user.setDaKichHoat(true);
            user.setDanhSachQuyen(List.of(role));

            return nguoiDungRepository.saveAndFlush(user);
        });

        assertThat(saved).isNotNull();
        return saved;
    }
}
