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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Do mat khau bi chan sau vai lan sai, nhung nguoi dung dang nhap dung khong bao gio bi khoa.
 * Thong bao that bai khong duoc tiet lo tai khoan nao ton tai.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class AuthAbuseControlIT {

    private static final String MAT_KHAU = "AuthAbuse@12345";

    @Autowired TestRestTemplate rest;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired QuyenRepository quyenRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager txManager;

    private String tenDangNhap;

    @BeforeEach
    void provisionUser() {
        tenDangNhap = "auth-abuse-" + System.nanoTime();
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Quyen quyen = quyenRepository.findByTenQuyen("USER");
            NguoiDung user = new NguoiDung();
            user.setHoDem("Auth");
            user.setTen("Abuse");
            user.setTenDangNhap(tenDangNhap);
            user.setMatKhau(passwordEncoder.encode(MAT_KHAU));
            user.setGioiTinh('X');
            user.setEmail(tenDangNhap + "@example.test");
            user.setDaKichHoat(true);
            user.setDanhSachQuyen(List.of(quyen));
            nguoiDungRepository.saveAndFlush(user);
        });
    }

    @AfterEach
    void cleanupUser() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            NguoiDung user = nguoiDungRepository.findByTenDangNhap(tenDangNhap);
            if (user != null) {
                nguoiDungRepository.deleteById((long) user.getMaNguoiDung());
            }
        });
    }

    @Test
    void doan_mat_khau_lien_tuc_bi_chan_bang_429() {
        for (int lan = 1; lan <= 5; lan++) {
            assertThat(dangNhap(tenDangNhap, "sai-mat-khau").getStatusCode())
                    .as("lan sai thu %d van tra loi thong thuong", lan)
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        assertThat(dangNhap(tenDangNhap, "sai-mat-khau").getStatusCode())
                .as("vuot nguong thi bi chan")
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void dang_nhap_dung_khong_bao_gio_bi_khoa() {
        for (int lan = 1; lan <= 12; lan++) {
            ResponseEntity<String> response = dangNhap(tenDangNhap, MAT_KHAU);

            assertThat(response.getStatusCode())
                    .as("dang nhap dung lan thu %d", lan)
                    .isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("eyJ");
        }
    }

    @Test
    void dang_nhap_dung_xoa_bo_dem_sai_truoc_do() {
        for (int lan = 1; lan <= 4; lan++) {
            assertThat(dangNhap(tenDangNhap, "sai-mat-khau").getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        assertThat(dangNhap(tenDangNhap, MAT_KHAU).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Bo dem da duoc xoa nen van con du han muc cho cac lan sai tiep theo.
        for (int lan = 1; lan <= 5; lan++) {
            assertThat(dangNhap(tenDangNhap, "sai-mat-khau").getStatusCode())
                    .as("lan sai thu %d sau khi reset", lan)
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void thong_bao_that_bai_khong_lo_tai_khoan_ton_tai() {
        String phanHoiTaiKhoanCo = noiDung(dangNhap(tenDangNhap, "sai-mat-khau"));
        String phanHoiTaiKhoanKhongCo = noiDung(dangNhap("khong-ton-tai-" + System.nanoTime(), "sai-mat-khau"));

        assertThat(phanHoiTaiKhoanCo).isEqualTo(phanHoiTaiKhoanKhongCo);
    }

    private ResponseEntity<String> dangNhap(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String payload = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        return rest.postForEntity("/tai-khoan/dang-nhap", new HttpEntity<>(payload, headers), String.class);
    }

    private String noiDung(ResponseEntity<String> response) {
        String body = response.getBody() == null ? "" : response.getBody();
        // Bo traceId/timestamp thay doi theo tung request, chi so sanh phan message on dinh.
        return body.replaceAll("\"traceId\":\"[^\"]*\"", "")
                .replaceAll("\"timestamp\":\"[^\"]*\"", "");
    }
}
