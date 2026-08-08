package com.example.book_be.nhantin.service;

import com.example.book_be.nhantin.domain.DangKyNhanTin;
import com.example.book_be.nhantin.repository.DangKyNhanTinRepository;
import com.example.book_be.shared.config.FrontendUrlProvider;
import com.example.book_be.shared.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit test thuan: khong Spring context, khong database, nen chay duoc ca khi Testcontainers
 * hong. Cac bat bien can database that (rang buoc UNIQUE, migration) nam o NhanTinIT.
 */
class NhanTinServiceTest {

    private DangKyNhanTinRepository repository;
    private EmailService emailService;
    private NhanTinService service;

    @BeforeEach
    void setUp() {
        repository = mock(DangKyNhanTinRepository.class);
        emailService = mock(EmailService.class);
        service = new NhanTinService(repository, emailService,
                new FrontendUrlProvider("http://localhost:3000"));
        when(repository.findByEmail(any())).thenReturn(Optional.empty());
    }

    @Test
    void luu_email_da_ha_chu_thuong_va_cat_khoang_trang() {
        service.dangKy("  Nguoi.Dung@Example.COM  ");

        DangKyNhanTin daLuu = batBanGhi();
        assertThat(daLuu.getEmail()).isEqualTo("nguoi.dung@example.com");
        assertThat(daLuu.getDaHuy()).isFalse();
        assertThat(daLuu.getMaHuy()).isNotBlank();
        assertThat(daLuu.getNgayDangKy()).isNotNull();
    }

    /** Dang ky moi chi la buoc mot: chua o trong danh sach cho toi khi bam lien ket. */
    @Test
    void dang_ky_moi_chua_duoc_xac_nhan_va_co_ma_xac_nhan() {
        service.dangKy("a@example.com");

        DangKyNhanTin daLuu = batBanGhi();
        assertThat(daLuu.getDaXacNhan()).isFalse();
        assertThat(daLuu.getMaXacNhan()).isNotBlank();
        assertThat(daLuu.getNgayXacNhan()).isNull();
    }

    @Test
    void gui_thu_xac_thuc_kem_lien_ket_chua_ma_xac_nhan() {
        service.dangKy("a@example.com");

        DangKyNhanTin daLuu = batBanGhi();
        ArgumentCaptor<String> than = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(eq("a@example.com"), anyString(), than.capture());
        assertThat(than.getValue())
                .contains("http://localhost:3000/xac-nhan-nhan-tin/" + daLuu.getMaXacNhan());
    }

    /**
     * Khong gui duoc thu thi dang ky khong bao gio hoan tat, nen bao thanh cong la noi doi.
     */
    @Test
    void khong_gui_duoc_thu_thi_bao_loi_chu_khong_bao_thanh_cong() {
        doThrow(new IllegalStateException("MAIL_FROM chua duoc cau hinh."))
                .when(emailService).sendEmail(any(), any(), any());

        assertThatThrownBy(() -> service.dangKy("a@example.com"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("503");
    }

    /** Da o trong danh sach roi thi khong gui lai — neu khong, o dang ky thanh cong cu spam. */
    @Test
    void dia_chi_da_xac_nhan_thi_khong_gui_lai_thu() {
        when(repository.findByEmail("a@example.com"))
                .thenReturn(Optional.of(banGhi("a@example.com", false, true)));

        service.dangKy("A@Example.com");

        verify(repository, never()).save(any());
        verify(emailService, never()).sendEmail(any(), any(), any());
    }

    @Test
    void dang_ky_lai_khi_chua_xac_nhan_thi_cap_ma_moi_va_gui_lai() {
        DangKyNhanTin choXacNhan = banGhi("a@example.com", false, false);
        String maCu = choXacNhan.getMaXacNhan();
        when(repository.findByEmail("a@example.com")).thenReturn(Optional.of(choXacNhan));

        service.dangKy("a@example.com");

        assertThat(choXacNhan.getMaXacNhan()).isNotEqualTo(maCu);
        verify(emailService).sendEmail(eq("a@example.com"), anyString(), anyString());
    }

    @Test
    void dang_ky_lai_sau_khi_da_huy_thi_phai_xac_nhan_lai() {
        DangKyNhanTin daHuy = banGhi("a@example.com", true, true);
        when(repository.findByEmail("a@example.com")).thenReturn(Optional.of(daHuy));

        service.dangKy("a@example.com");

        assertThat(daHuy.getDaHuy()).isFalse();
        assertThat(daHuy.getDaXacNhan()).isFalse();
        verify(emailService).sendEmail(eq("a@example.com"), anyString(), anyString());
    }

    @Test
    void thua_khoa_trung_thi_coi_nhu_da_dang_ky_chu_khong_bao_loi() {
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("trung khoa"));
        when(repository.findByEmail("a@example.com"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(banGhi("a@example.com", false, false)));

        service.dangKy("a@example.com");

        verify(emailService, never()).sendEmail(any(), any(), any());
    }

    @Test
    void loi_ghi_that_su_van_duoc_nem_ra() {
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("loi khac"));
        when(repository.findByEmail("a@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.dangKy("a@example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void tu_choi_email_khong_hop_le() {
        for (String xau : new String[]{null, "", "   ", "khong-co-a-cong", "a@b", "a@@b.com", "a b@c.com"}) {
            assertThatThrownBy(() -> service.dangKy(xau))
                    .as("email %s phai bi tu choi", xau)
                    .isInstanceOf(ResponseStatusException.class);
        }
        verify(repository, never()).save(any());
        verify(emailService, never()).sendEmail(any(), any(), any());
    }

    @Test
    void tu_choi_email_qua_dai() {
        String qua_dai = "a".repeat(250) + "@example.com";

        assertThatThrownBy(() -> service.dangKy(qua_dai)).isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void xac_nhan_dua_dia_chi_vao_danh_sach_va_xoa_ma_da_dung() {
        DangKyNhanTin ban = banGhi("a@example.com", false, false);
        when(repository.findByMaXacNhan("ma-xac-nhan-1")).thenReturn(Optional.of(ban));

        service.xacNhan("ma-xac-nhan-1");

        assertThat(ban.getDaXacNhan()).isTrue();
        assertThat(ban.getNgayXacNhan()).isNotNull();
        assertThat(ban.getMaXacNhan()).as("khoa da dung phai bi xoa").isNull();
    }

    @Test
    void xac_nhan_bang_ma_sai_tra_404() {
        when(repository.findByMaXacNhan(eq("khong-ton-tai"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.xacNhan("khong-ton-tai"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void ma_xac_nhan_qua_bay_ngay_thi_het_han() {
        DangKyNhanTin cu = banGhi("a@example.com", false, false);
        cu.setNgayDangKy(Instant.now().minus(8, ChronoUnit.DAYS));
        when(repository.findByMaXacNhan("ma-xac-nhan-1")).thenReturn(Optional.of(cu));

        assertThatThrownBy(() -> service.xacNhan("ma-xac-nhan-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("410");
        assertThat(cu.getDaXacNhan()).isFalse();
    }

    @Test
    void huy_bang_ma_hop_le_thi_danh_dau_da_huy() {
        DangKyNhanTin ban = banGhi("a@example.com", false, true);
        when(repository.findByMaHuy("ma-huy-1")).thenReturn(Optional.of(ban));

        service.huy("ma-huy-1");

        assertThat(ban.getDaHuy()).isTrue();
        verify(repository).save(ban);
    }

    /** Ma sai tra 404 chu khong tiet lo email nao dang ton tai trong he thong. */
    @Test
    void huy_bang_ma_sai_tra_404() {
        when(repository.findByMaHuy(eq("khong-ton-tai"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.huy("khong-ton-tai"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void huy_hai_lan_khong_ghi_them() {
        DangKyNhanTin ban = banGhi("a@example.com", true, true);
        when(repository.findByMaHuy("ma-huy-1")).thenReturn(Optional.of(ban));

        service.huy("ma-huy-1");

        verify(repository, never()).save(any());
    }

    private DangKyNhanTin batBanGhi() {
        ArgumentCaptor<DangKyNhanTin> bat = ArgumentCaptor.forClass(DangKyNhanTin.class);
        verify(repository).save(bat.capture());
        return bat.getValue();
    }

    private DangKyNhanTin banGhi(String email, boolean daHuy, boolean daXacNhan) {
        DangKyNhanTin ban = new DangKyNhanTin();
        ban.setMaDangKy(1L);
        ban.setEmail(email);
        ban.setMaHuy("ma-huy-1");
        ban.setMaXacNhan(daXacNhan ? null : "ma-xac-nhan-1");
        ban.setNgayDangKy(Instant.now());
        ban.setDaHuy(daHuy);
        ban.setDaXacNhan(daXacNhan);
        return ban;
    }
}
