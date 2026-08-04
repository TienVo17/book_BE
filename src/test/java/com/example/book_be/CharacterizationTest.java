package com.example.book_be;

import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.repository.QuyenRepository;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.domain.Quyen;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization tests — khoa hanh vi hien tai cua cac endpoint trong yeu de bat regression
 * khi refactor package. RT-2: chi assert field on dinh, khong so full-JSON.
 * DB that qua Testcontainers ({@link TestcontainersConfig}), Flyway seed du lieu demo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class CharacterizationTest {

    private static final String ADMIN_PASS = "Smoke@12345";

    @Autowired TestRestTemplate rest;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired QuyenRepository quyenRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager txManager;

    private String adminUser;

    /**
     * Tu tao admin rieng cho test. Cac tai khoan seed cong khai da bi V10 vo hieu hoa vinh vien,
     * nen khong the muon lai chung de dang nhap.
     */
    @BeforeEach
    void provisionAdmin() {
        adminUser = "characterization-admin-" + System.nanoTime();
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Quyen quyenAdmin = quyenRepository.findByTenQuyen("ADMIN");
            assertThat(quyenAdmin).as("quyen ADMIN da duoc seed").isNotNull();

            NguoiDung admin = new NguoiDung();
            admin.setHoDem("Characterization");
            admin.setTen("Admin");
            admin.setTenDangNhap(adminUser);
            admin.setMatKhau(passwordEncoder.encode(ADMIN_PASS));
            admin.setGioiTinh('X');
            admin.setEmail(adminUser + "@example.test");
            admin.setDaKichHoat(true);
            admin.setDanhSachQuyen(List.of(quyenAdmin));
            nguoiDungRepository.saveAndFlush(admin);
        });
    }

    @AfterEach
    void cleanupAdmin() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            NguoiDung admin = nguoiDungRepository.findByTenDangNhap(adminUser);
            if (admin != null) {
                nguoiDungRepository.deleteById((long) admin.getMaNguoiDung());
            }
        });
    }

    @Test
    void catalog_tra_ve_sach() {
        ResponseEntity<String> r = rest.getForEntity("/api/sach?page=0", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("\"maSach\"");
    }

    @Test
    void the_loai_cong_khai_200() {
        assertThat(rest.getForEntity("/api/the-loai", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void admin_user_an_danh_bi_chan() {
        int code = rest.getForEntity("/api/admin/user?page=0", String.class).getStatusCode().value();
        assertThat(code).as("an danh phai bi tu choi").isIn(401, 403);
    }

    @Test
    void admin_user_co_token_khong_lo_mat_khau() {
        String jwt = login(adminUser, ADMIN_PASS);
        assertThat(jwt).as("login admin tra JWT").isNotBlank();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        ResponseEntity<String> r = rest.exchange(
                "/api/admin/user?page=0", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody())
                .as("KHONG duoc lo hash/mat khau ra API")
                .doesNotContain("\"matKhau\"")
                .doesNotContain("$2a$");
    }

    private String login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        ResponseEntity<String> r = rest.postForEntity(
                "/tai-khoan/dang-nhap", new HttpEntity<>(body, headers), String.class);
        String content = r.getBody() == null ? "" : r.getBody();
        Matcher m = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+").matcher(content);
        return m.find() ? m.group() : "";
    }
}
