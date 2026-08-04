package com.example.book_be.portfolio;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cac tai khoan seed cong khai trong V3/V4 (mat khau "1" duoc ghi thang trong repository public)
 * phai khong con dang nhap duoc sau khi chuoi migration chay xong — ca tren database moi lan
 * database da ton tai truoc do.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class SeedCredentialLockdownIT {

    private static final List<String> TEN_DANG_NHAP_SEED =
            List.of("admin", "user1", "user2", "user3", "user4", "user5");
    private static final String MAT_KHAU_SEED_CU = "1";

    @Autowired TestRestTemplate rest;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void moi_tai_khoan_seed_deu_khong_dang_nhap_duoc() {
        for (String tenDangNhap : TEN_DANG_NHAP_SEED) {
            ResponseEntity<String> response = dangNhap(tenDangNhap, MAT_KHAU_SEED_CU);

            assertThat(response.getStatusCode().is2xxSuccessful())
                    .as("dang nhap seed '%s' phai that bai", tenDangNhap)
                    .isFalse();
            assertThat(response.getBody() == null ? "" : response.getBody())
                    .as("khong duoc phat JWT cho '%s'", tenDangNhap)
                    .doesNotContain("eyJ");
        }
    }

    @Test
    void tai_khoan_seed_bi_vo_hieu_hoa_va_khong_con_quyen() {
        for (String tenDangNhap : TEN_DANG_NHAP_SEED) {
            NguoiDung nguoiDung = nguoiDungRepository.findByTenDangNhap(tenDangNhap);
            if (nguoiDung == null) {
                continue;
            }

            assertThat(nguoiDung.getDaKichHoat())
                    .as("'%s' phai bi vo hieu hoa", tenDangNhap)
                    .isFalse();
            assertThat(nguoiDung.getMatKhau())
                    .as("'%s' khong con giu hash seed cong khai", tenDangNhap)
                    .startsWith("!disabled-seed-");

            Integer soQuyen = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `nguoidung_quyen` WHERE `ma_nguoi_dung` = ?",
                    Integer.class, nguoiDung.getMaNguoiDung());
            assertThat(soQuyen).as("'%s' khong con quyen nao", tenDangNhap).isZero();
        }
    }

    @Test
    void khong_con_admin_kich_hoat_nao_dung_hash_seed() {
        Integer soAdminSeed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `nguoi_dung` nd "
                        + "JOIN `nguoidung_quyen` nq ON nq.`ma_nguoi_dung` = nd.`ma_nguoi_dung` "
                        + "JOIN `quyen` q ON q.`ma_quyen` = nq.`ma_quyen` "
                        + "WHERE q.`ten_quyen` = 'ADMIN' AND nd.`da_kich_hoat` = 1 "
                        + "AND nd.`mat_khau` LIKE '$2a$10$B6qPwSi5FHcaX4a34FwqRu%'",
                Integer.class);

        assertThat(soAdminSeed).isZero();
    }

    private ResponseEntity<String> dangNhap(String tenDangNhap, String matKhau) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String payload = "{\"username\":\"" + tenDangNhap + "\",\"password\":\"" + matKhau + "\"}";
        return rest.postForEntity("/tai-khoan/dang-nhap", new HttpEntity<>(payload, headers), String.class);
    }
}
