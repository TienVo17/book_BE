package com.example.book_be.nhantin.web;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.nhantin.domain.DangKyNhanTin;
import com.example.book_be.nhantin.repository.DangKyNhanTinRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bat bien can database that: rang buoc UNIQUE, migration V17, va viec repository KHONG bi
 * Spring Data REST phoi ra ngoai.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class NhanTinIT {

    @Autowired TestRestTemplate rest;
    @Autowired DangKyNhanTinRepository repository;

    private String emailDaDung;

    @AfterEach
    void donFixture() {
        if (emailDaDung != null) {
            repository.findByEmail(emailDaDung).ifPresent(repository::delete);
            emailDaDung = null;
        }
    }

    @Test
    void dang_ky_luu_duoc_va_ha_chu_thuong() {
        emailDaDung = "Footer.Test." + System.nanoTime() + "@Example.COM";

        ResponseEntity<String> response = guiDangKy(emailDaDung);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        emailDaDung = emailDaDung.toLowerCase(java.util.Locale.ROOT);
        Optional<DangKyNhanTin> daLuu = repository.findByEmail(emailDaDung);
        assertThat(daLuu).isPresent();
        assertThat(daLuu.get().getDaHuy()).isFalse();
        assertThat(daLuu.get().getMaHuy()).isNotBlank();
    }

    /** Bam hai lan la chuyen binh thuong; khong duoc bao loi cho mot thao tac da thanh cong. */
    @Test
    void dang_ky_hai_lan_van_200_va_chi_mot_dong() {
        emailDaDung = "footer.trung." + System.nanoTime() + "@example.com";

        assertThat(guiDangKy(emailDaDung).getStatusCode().value()).isEqualTo(200);
        assertThat(guiDangKy(emailDaDung).getStatusCode().value()).isEqualTo(200);

        assertThat(repository.findAll().stream()
                .filter(dong -> emailDaDung.equals(dong.getEmail()))
                .count())
                .as("khong duoc tao dong thu hai cho cung mot email")
                .isEqualTo(1);
    }

    @Test
    void email_khong_hop_le_tra_400() {
        ResponseEntity<String> response = guiDangKy("khong-phai-email");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void huy_bang_ma_hop_le_thi_danh_dau_da_huy() {
        emailDaDung = "footer.huy." + System.nanoTime() + "@example.com";
        guiDangKy(emailDaDung);
        String maHuy = repository.findByEmail(emailDaDung).orElseThrow().getMaHuy();

        ResponseEntity<String> response = rest.postForEntity(
                "/api/nhan-tin/huy/{maHuy}", new HttpEntity<>(new HttpHeaders()), String.class, maHuy);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(repository.findByEmail(emailDaDung).orElseThrow().getDaHuy()).isTrue();
    }

    @Test
    void huy_bang_ma_sai_tra_404() {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/nhan-tin/huy/{maHuy}", new HttpEntity<>(new HttpHeaders()), String.class,
                "ma-khong-ton-tai-" + System.nanoTime());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    /**
     * Bang nay la mot danh sach dia chi email. De Spring Data REST export no ra
     * {@code /dangKyNhanTins} la mo mot endpoint cong khai doc duoc toan bo danh sach —
     * dung lop loi vua phai dong lai o SachRepository.
     */
    @Test
    void repository_khong_bi_phoi_qua_spring_data_rest() {
        for (String duong : new String[]{"/dangKyNhanTins", "/dang_ky_nhan_tins", "/dangKyNhanTins/search"}) {
            ResponseEntity<String> response = rest.getForEntity(duong, String.class);
            assertThat(response.getStatusCode().is2xxSuccessful())
                    .as("%s khong duoc mo cong khai", duong)
                    .isFalse();
        }
    }

    private ResponseEntity<String> guiDangKy(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String than = "{\"email\":\"" + email + "\"}";
        return rest.postForEntity("/api/nhan-tin/dang-ky", new HttpEntity<>(than, headers), String.class);
    }
}
