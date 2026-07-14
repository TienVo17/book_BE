package com.example.book_be.sach.web;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.sach.dto.SachAdminUpsertBo;
import com.example.book_be.sach.repository.SachRepository;
import com.example.book_be.sach.service.SachService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** HTTP contracts for the future signed stock-delta route and Data REST write closure. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class SachAdminTonKhoControllerIT {

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "StockContract@123";
    private static final String USER_USER = "user1";
    private static final String USER_PASS = "StockContractUser@123";

    @Autowired TestRestTemplate rest;
    @Autowired SachRepository sachRepository;
    @Autowired SachService sachService;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager txManager;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final List<Integer> sachFixtures = new ArrayList<>();
    private String matKhauAdminCu;
    private String matKhauUserCu;

    @BeforeEach
    void provisionCredentials() {
        rest.getRestTemplate().setRequestFactory(
                new JdkClientHttpRequestFactory(httpClient));
        matKhauAdminCu = datMatKhau(ADMIN_USER, ADMIN_PASS);
        matKhauUserCu = datMatKhau(USER_USER, USER_PASS);
    }

    @AfterEach
    void cleanup() {
        for (Integer maSach : sachFixtures) {
            sachRepository.deleteById((long) maSach);
        }
        khoiPhucMatKhau(ADMIN_USER, matKhauAdminCu);
        khoiPhucMatKhau(USER_USER, matKhauUserCu);
    }

    @Test
    void patch_ton_kho_admin_thanh_cong_tra_ve_ma_sach_va_ton_authoritative() {
        Sach sach = taoSach(8);

        ResponseEntity<String> response = patch(sach.getMaSach(), "{\"soLuongThayDoi\":5}", adminHeaders());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"maSach\":" + sach.getMaSach())
                .contains("\"soLuongTon\":13");
        assertThat(tonKho(sach.getMaSach())).isEqualTo(13);
    }

    @Test
    void patch_ton_kho_tu_choi_body_sai_zero_va_malformed_voi_error_on_dinh() {
        Sach sach = taoSach(8);

        for (String body : List.of(
                "{}",
                "{\"soLuongThayDoi\":0}",
                "{\"soLuongThayDoi\":1.5}",
                "{\"soLuongThayDoi\":1e-1}",
                "{\"soLuongThayDoi\":2147483648}",
                "{\"soLuongThayDoi\":-2147483649}",
                "{not-json")) {
            ResponseEntity<String> response = patch(sach.getMaSach(), body, adminHeaders());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).contains("\"error\"");
            assertThat(tonKho(sach.getMaSach())).isEqualTo(8);
        }
    }

    @Test
    void patch_ton_kho_tra_404_khi_sach_khong_ton_tai_va_409_khi_giam_xuong_am() {
        ResponseEntity<String> missing = patch(999_999, "{\"soLuongThayDoi\":1}", adminHeaders());
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).contains("\"error\"");

        Sach sach = taoSach(8);
        ResponseEntity<String> belowZero = patch(sach.getMaSach(), "{\"soLuongThayDoi\":-9}", adminHeaders());
        assertThat(belowZero.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(belowZero.getBody()).contains("\"error\"");
        assertThat(tonKho(sach.getMaSach())).isEqualTo(8);
    }

    @Test
    void patch_ton_kho_yeu_cau_admin_va_patch_preflight_duoc_cho_phep() {
        Sach sach = taoSach(8);
        HttpHeaders cors = new HttpHeaders();
        cors.setOrigin("http://localhost:3000");
        cors.setAccessControlRequestMethod(HttpMethod.PATCH);

        ResponseEntity<String> anonymous = patch(sach.getMaSach(), "{\"soLuongThayDoi\":1}", new HttpHeaders());
        ResponseEntity<String> normalUser = patch(sach.getMaSach(), "{\"soLuongThayDoi\":1}", userHeaders());
        ResponseEntity<String> preflight = rest.exchange(
                "/api/admin/sach/{id}/ton-kho", HttpMethod.OPTIONS, new HttpEntity<>(cors), String.class, sach.getMaSach());

        assertThat(anonymous.getStatusCode().value()).isIn(401, 403);
        assertThat(normalUser.getStatusCode().value()).isIn(401, 403);
        assertThat(preflight.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(preflight.getHeaders().getAccessControlAllowMethods()).contains(HttpMethod.PATCH);
    }

    @Test
    void legacy_put_metadata_stale_stock_khong_duoc_ghi_de_stock_atomic_moi() {
        Sach sach = taoSach(10);
        sach.setSoLuong(8);
        sachRepository.saveAndFlush(sach);

        Map<String, Object> stalePayload = Map.of(
                "tenSach", sach.getTenSach(),
                "tenTacGia", "Metadata HTTP contract",
                "moTaChiTiet", "Updated metadata only",
                "giaNiemYet", 20000,
                "giaBan", 10000,
                "soLuongTon", 10,
                "isActive", 1,
                "maTheLoaiList", List.of(),
                "listImageStr", List.of());
        ResponseEntity<String> response = rest.exchange(
                "/api/admin/sach/update/{id}", HttpMethod.PUT,
                new HttpEntity<>(stalePayload, adminHeaders()), String.class, sach.getMaSach());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        MetadataSnapshot updated = new TransactionTemplate(txManager).execute(status -> {
            Sach actual = sachRepository.findById((long) sach.getMaSach()).orElseThrow();
            return new MetadataSnapshot(
                    actual.getSoLuong(),
                    actual.getTenTacGia(),
                    actual.getMoTaChiTiet(),
                    actual.getListTheLoai().isEmpty(),
                    actual.getListHinhAnh().isEmpty());
        });
        assertThat(updated).isNotNull();
        assertThat(updated.soLuong()).isEqualTo(8);
        assertThat(updated.tenTacGia()).isEqualTo("Metadata HTTP contract");
        assertThat(updated.moTaChiTiet()).isEqualTo("Updated metadata only");
        assertThat(updated.theLoaiRong()).isTrue();
        assertThat(updated.hinhAnhRong()).isTrue();
    }

    @Test
    void data_rest_sach_write_routes_va_association_mutation_deu_bi_tu_choi_nhung_relation_get_van_hoat_dong() {
        Sach sach = taoSach(8);
        HttpHeaders headers = adminHeaders();
        Map<String, Object> rawBook = Map.of("tenSach", "Raw REST mutation", "soLuong", 999);

        ResponseEntity<String> post = rest.exchange("/sach", HttpMethod.POST,
                new HttpEntity<>(rawBook, headers), String.class);
        ResponseEntity<String> put = rest.exchange("/sach/{id}", HttpMethod.PUT,
                new HttpEntity<>(rawBook, headers), String.class, sach.getMaSach());
        ResponseEntity<String> patch = rest.exchange("/sach/{id}", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("soLuong", 999), headers), String.class, sach.getMaSach());
        ResponseEntity<String> delete = rest.exchange("/sach/{id}", HttpMethod.DELETE,
                new HttpEntity<>(headers), String.class, sach.getMaSach());
        HttpHeaders textUriListHeaders = new HttpHeaders();
        textUriListHeaders.putAll(headers);
        textUriListHeaders.setContentType(MediaType.parseMediaType("text/uri-list"));
        ResponseEntity<String> associationPut = rest.exchange("/sach/{id}/listTheLoai", HttpMethod.PUT,
                new HttpEntity<>("/theLoais/1", textUriListHeaders), String.class, sach.getMaSach());
        ResponseEntity<String> associationDelete = rest.exchange("/sach/{id}/listTheLoai", HttpMethod.DELETE,
                new HttpEntity<>(headers), String.class, sach.getMaSach());
        ResponseEntity<String> relationGet = rest.getForEntity("/sach/{id}/listDanhGia", String.class, sach.getMaSach());

        assertThat(post.getStatusCode().value()).isIn(403, 405);
        assertThat(put.getStatusCode().value()).isIn(403, 405);
        assertThat(patch.getStatusCode().value()).isIn(403, 405);
        assertThat(delete.getStatusCode().value()).isIn(403, 405);
        assertThat(associationPut.getStatusCode().value()).isIn(403, 405);
        assertThat(associationDelete.getStatusCode().value()).isIn(403, 405);
        assertThat(tonKho(sach.getMaSach())).isEqualTo(8);
        Boolean theLoaiRong = new TransactionTemplate(txManager).execute(status ->
                sachRepository.findById((long) sach.getMaSach())
                        .orElseThrow()
                        .getListTheLoai()
                        .isEmpty());
        assertThat(theLoaiRong).isTrue();
        assertThat(relationGet.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void raw_api_sach_update_khong_la_duong_bypass_cho_bat_ky_principal_nao() {
        Sach sach = taoSach(8);
        Map<String, Object> payload = Map.of("soLuong", 999);

        for (HttpHeaders headers : List.of(new HttpHeaders(), userHeaders(), adminHeaders())) {
            ResponseEntity<String> response = rest.exchange("/api/sach/update/{id}", HttpMethod.PUT,
                    new HttpEntity<>(payload, headers), String.class, sach.getMaSach());
            assertThat(response.getStatusCode().value()).isIn(401, 403, 404, 405);
            assertThat(tonKho(sach.getMaSach())).isEqualTo(8);
        }
    }

    private ResponseEntity<String> patch(int maSach, String json, HttpHeaders headers) {
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.putAll(headers);
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/api/admin/sach/{id}/ton-kho", HttpMethod.PATCH,
                new HttpEntity<>(json, requestHeaders), String.class, maSach);
    }

    private HttpHeaders adminHeaders() {
        return bearerHeaders(login(ADMIN_USER, ADMIN_PASS));
    }

    private HttpHeaders userHeaders() {
        return bearerHeaders(login(USER_USER, USER_PASS));
    }

    private HttpHeaders bearerHeaders(String jwt) {
        assertThat(jwt).isNotBlank();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        return headers;
    }

    private String login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity("/tai-khoan/dang-nhap",
                new HttpEntity<>("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}", headers),
                String.class);
        Matcher matcher = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")
                .matcher(response.getBody() == null ? "" : response.getBody());
        return matcher.find() ? matcher.group() : "";
    }

    private Sach taoSach(int soLuongTon) {
        SachAdminUpsertBo bo = new SachAdminUpsertBo();
        bo.setTenSach("Stock HTTP fixture " + System.nanoTime());
        bo.setTenTacGia("Stock HTTP author");
        bo.setMoTaChiTiet("Stock HTTP description");
        bo.setGiaNiemYet(20000D);
        bo.setGiaBan(10000D);
        bo.setSoLuongTon(soLuongTon);
        bo.setIsActive(1);
        bo.setMaTheLoaiList(List.of());
        bo.setListImageStr(List.of());
        Sach sach = sachService.save(bo);
        sachFixtures.add(sach.getMaSach());
        return sach;
    }

    private int tonKho(int maSach) {
        return sachRepository.findById((long) maSach).orElseThrow().getSoLuong();
    }

    private String datMatKhau(String tenDangNhap, String matKhau) {
        NguoiDung user = nguoiDungRepository.findByTenDangNhap(tenDangNhap);
        assertThat(user).as("seed user %s", tenDangNhap).isNotNull();
        String old = user.getMatKhau();
        user.setMatKhau(passwordEncoder.encode(matKhau));
        nguoiDungRepository.saveAndFlush(user);
        return old;
    }

    private void khoiPhucMatKhau(String tenDangNhap, String matKhauCu) {
        if (matKhauCu == null) {
            return;
        }
        NguoiDung user = nguoiDungRepository.findByTenDangNhap(tenDangNhap);
        if (user != null) {
            user.setMatKhau(matKhauCu);
            nguoiDungRepository.saveAndFlush(user);
        }
    }

    private record MetadataSnapshot(
            int soLuong,
            String tenTacGia,
            String moTaChiTiet,
            boolean theLoaiRong,
            boolean hinhAnhRong) {
    }
}
