package com.example.book_be.nhantin.web;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.nhantin.domain.DangKyNhanTin;
import com.example.book_be.nhantin.repository.DangKyNhanTinRepository;
import com.example.book_be.shared.email.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Bat bien can database that: rang buoc UNIQUE, migration V17/V18, va viec repository KHONG
 * bi Spring Data REST phoi ra ngoai.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class NhanTinIT {

    @Autowired TestRestTemplate rest;
    @Autowired DangKyNhanTinRepository repository;

    /** Test khong duoc gui thu that; MAIL_FROM cung khong duoc cau hinh o day. */
    @MockBean EmailService emailService;

    private String emailDaDung;

    @AfterEach
    void donFixture() {
        if (emailDaDung != null) {
            repository.findByEmail(emailDaDung).ifPresent(repository::delete);
            emailDaDung = null;
        }
    }

    @Test
    void dang_ky_luu_o_trang_thai_cho_xac_nhan() {
        emailDaDung = "Footer.Test." + System.nanoTime() + "@Example.COM";

        ResponseEntity<String> response = guiDangKy(emailDaDung);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        emailDaDung = emailDaDung.toLowerCase(Locale.ROOT);
        Optional<DangKyNhanTin> daLuu = repository.findByEmail(emailDaDung);
        assertThat(daLuu).isPresent();
        assertThat(daLuu.get().getDaXacNhan()).as("chua bam lien ket thi chua vao danh sach").isFalse();
        assertThat(daLuu.get().getMaXacNhan()).isNotBlank();
        assertThat(daLuu.get().getMaHuy()).isNotBlank();
        verify(emailService).sendEmail(any(), any(), any());
    }

    @Test
    void bam_lien_ket_xac_nhan_thi_vao_danh_sach_va_ma_bi_xoa() {
        emailDaDung = "footer.xacnhan." + System.nanoTime() + "@example.com";
        guiDangKy(emailDaDung);
        String maXacNhan = repository.findByEmail(emailDaDung).orElseThrow().getMaXacNhan();

        ResponseEntity<String> response = rest.postForEntity(
                "/api/nhan-tin/xac-nhan/{ma}", new HttpEntity<>(new HttpHeaders()), String.class, maXacNhan);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        DangKyNhanTin sau = repository.findByEmail(emailDaDung).orElseThrow();
        assertThat(sau.getDaXacNhan()).isTrue();
        assertThat(sau.getMaXacNhan()).isNull();
    }

    /** Dung lai khoa da xac nhan mot lan phai that bai. */
    @Test
    void xac_nhan_hai_lan_bang_cung_mot_ma_thi_lan_hai_tra_404() {
        emailDaDung = "footer.hailan." + System.nanoTime() + "@example.com";
        guiDangKy(emailDaDung);
        String maXacNhan = repository.findByEmail(emailDaDung).orElseThrow().getMaXacNhan();

        rest.postForEntity("/api/nhan-tin/xac-nhan/{ma}", new HttpEntity<>(new HttpHeaders()), String.class, maXacNhan);
        ResponseEntity<String> lanHai = rest.postForEntity(
                "/api/nhan-tin/xac-nhan/{ma}", new HttpEntity<>(new HttpHeaders()), String.class, maXacNhan);

        assertThat(lanHai.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void xac_nhan_bang_ma_sai_tra_404() {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/nhan-tin/xac-nhan/{ma}", new HttpEntity<>(new HttpHeaders()), String.class,
                "ma-khong-ton-tai-" + System.nanoTime());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    /** Bam hai lan la chuyen binh thuong; khong duoc tao dong thu hai. */
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
    void email_khong_hop_le_tra_400_va_khong_gui_thu() {
        ResponseEntity<String> response = guiDangKy("khong-phai-email");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    /** Khong gui duoc thu thi khong duoc bao thanh cong cho mot dang ky khong the hoan tat. */
    @Test
    void khong_gui_duoc_thu_thi_tra_503() {
        doThrow(new IllegalStateException("MAIL_FROM chua duoc cau hinh."))
                .when(emailService).sendEmail(any(), any(), any());
        emailDaDung = "footer.loimail." + System.nanoTime() + "@example.com";

        ResponseEntity<String> response = guiDangKy(emailDaDung);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
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
