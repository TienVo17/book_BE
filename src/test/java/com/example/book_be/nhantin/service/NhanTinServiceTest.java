package com.example.book_be.nhantin.service;

import com.example.book_be.nhantin.domain.DangKyNhanTin;
import com.example.book_be.nhantin.repository.DangKyNhanTinRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit test thuan: khong Spring context, khong database, nen chay duoc ca khi Testcontainers
 * hong. Cac bat bien can database that (rang buoc UNIQUE, migration) nam o NhanTinIT.
 */
class NhanTinServiceTest {

    private DangKyNhanTinRepository repository;
    private NhanTinService service;

    @BeforeEach
    void setUp() {
        repository = mock(DangKyNhanTinRepository.class);
        service = new NhanTinService(repository);
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

    /** Hai dia chi chi khac hoa thuong khong duoc thanh hai dong. */
    @Test
    void dang_ky_lai_khong_tao_them_dong() {
        when(repository.findByEmail("a@example.com")).thenReturn(Optional.of(banGhi("a@example.com", false)));

        service.dangKy("A@Example.com");

        verify(repository, never()).save(any());
    }

    @Test
    void dang_ky_lai_sau_khi_da_huy_thi_bat_lai() {
        DangKyNhanTin daHuy = banGhi("a@example.com", true);
        when(repository.findByEmail("a@example.com")).thenReturn(Optional.of(daHuy));

        service.dangKy("a@example.com");

        assertThat(daHuy.getDaHuy()).isFalse();
        verify(repository).save(daHuy);
    }

    /**
     * Hai request cung email den cung luc deu thay findByEmail rong roi cung ghi. Rang buoc
     * UNIQUE chan dong thu hai; nguoi dung khong duoc thay loi cho mot thao tac da thanh cong.
     */
    @Test
    void thua_khoa_trung_thi_coi_nhu_da_dang_ky_chu_khong_bao_loi() {
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("trung khoa"));
        when(repository.findByEmail("a@example.com"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(banGhi("a@example.com", false)));

        service.dangKy("a@example.com");
    }

    /** Neu that su khong phai trung khoa thi khong duoc nuot loi. */
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
    }

    @Test
    void tu_choi_email_qua_dai() {
        String qua_dai = "a".repeat(250) + "@example.com";

        assertThatThrownBy(() -> service.dangKy(qua_dai)).isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void huy_bang_ma_hop_le_thi_danh_dau_da_huy() {
        DangKyNhanTin ban = banGhi("a@example.com", false);
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
        DangKyNhanTin ban = banGhi("a@example.com", true);
        when(repository.findByMaHuy("ma-huy-1")).thenReturn(Optional.of(ban));

        service.huy("ma-huy-1");

        verify(repository, never()).save(any());
    }

    private DangKyNhanTin batBanGhi() {
        var bat = org.mockito.ArgumentCaptor.forClass(DangKyNhanTin.class);
        verify(repository).save(bat.capture());
        return bat.getValue();
    }

    private DangKyNhanTin banGhi(String email, boolean daHuy) {
        DangKyNhanTin ban = new DangKyNhanTin();
        ban.setMaDangKy(1L);
        ban.setEmail(email);
        ban.setMaHuy("ma-huy-1");
        ban.setNgayDangKy(Instant.now());
        ban.setDaHuy(daHuy);
        return ban;
    }
}
