package com.example.book_be.shared.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Che ten phai chay o backend, va phai chiu duoc moi hinh dang ten that.
 *
 * <p>Mot ham che ten nem NPE hay tra chuoi rong khi gap du lieu thieu se lam ca trang
 * san pham hong — trong khi viec no dang lam chi la giau bot mot phan cua cai ten.
 */
class TenHienThiUtilTest {

    @Test
    void ten_ba_tu_giu_ho_va_rut_gon_phan_con_lai() {
        assertThat(TenHienThiUtil.che("Nguyễn Văn", "An")).isEqualTo("Nguyễn V. A.");
    }

    @Test
    void ten_hai_tu() {
        assertThat(TenHienThiUtil.che("Trần", "Lan")).isEqualTo("Trần L.");
    }

    /** Chi con mot tu thi khong con gi de che — rut gon no se chi con mot chu cai. */
    @Test
    void ten_mot_tu_giu_nguyen() {
        assertThat(TenHienThiUtil.che("", "Lan")).isEqualTo("Lan");
        assertThat(TenHienThiUtil.che(null, "Lan")).isEqualTo("Lan");
    }

    @Test
    void ten_nhieu_tu() {
        assertThat(TenHienThiUtil.che("Nguyễn Thị Ngọc", "Ánh"))
                .isEqualTo("Nguyễn T. N. Á.");
    }

    /** Khoang trang thua giua cac tu la chuyen thuong gap trong du lieu nhap tay. */
    @Test
    void khoang_trang_thua_khong_sinh_dau_cham_rong() {
        assertThat(TenHienThiUtil.che("  Lê   Văn  ", "  Bình ")).isEqualTo("Lê V. B.");
    }

    @Test
    void khong_co_ten_thi_tra_ve_nhan_an_danh_chu_khong_phai_chuoi_rong() {
        assertThat(TenHienThiUtil.che(null, null)).isEqualTo(TenHienThiUtil.TEN_AN_DANH);
        assertThat(TenHienThiUtil.che("", "")).isEqualTo(TenHienThiUtil.TEN_AN_DANH);
        assertThat(TenHienThiUtil.che("   ", "   ")).isEqualTo(TenHienThiUtil.TEN_AN_DANH);
    }
}
