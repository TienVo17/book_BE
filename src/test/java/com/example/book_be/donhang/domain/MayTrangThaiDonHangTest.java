package com.example.book_be.donhang.domain;

import org.junit.jupiter.api.Test;

import static com.example.book_be.donhang.domain.TrangThaiGiaoHang.CHO_XU_LY;
import static com.example.book_be.donhang.domain.TrangThaiGiaoHang.DANG_GIAO;
import static com.example.book_be.donhang.domain.TrangThaiGiaoHang.DA_GIAO;
import static com.example.book_be.donhang.domain.TrangThaiGiaoHang.DA_HUY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test thuan (khong Spring/Testcontainers) cho may trang thai don hang.
 * Khoa 2 co che chuyen trang thai: chuyen-co-dich (idempotent target) va tien-1-buoc (advance).
 */
class MayTrangThaiDonHangTest {

    // --- Chuyen co dich: hop le 0->1, 0->3, 1->3 ---

    @Test
    void chuyen_co_dich_hop_le_khong_throw() {
        assertThatCode(() -> MayTrangThaiDonHang.kiemTraChuyenCoDich(CHO_XU_LY, DANG_GIAO)).doesNotThrowAnyException();
        assertThatCode(() -> MayTrangThaiDonHang.kiemTraChuyenCoDich(CHO_XU_LY, DA_HUY)).doesNotThrowAnyException();
        assertThatCode(() -> MayTrangThaiDonHang.kiemTraChuyenCoDich(DANG_GIAO, DA_HUY)).doesNotThrowAnyException();
    }

    @Test
    void chuyen_co_dich_khong_hop_le_throw() {
        // 2->0 (acceptance criterion 2), lui trang thai, chuyen tu trang thai cuoi
        assertThatThrownBy(() -> MayTrangThaiDonHang.kiemTraChuyenCoDich(DA_GIAO, CHO_XU_LY)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MayTrangThaiDonHang.kiemTraChuyenCoDich(DA_GIAO, DANG_GIAO)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MayTrangThaiDonHang.kiemTraChuyenCoDich(DANG_GIAO, CHO_XU_LY)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MayTrangThaiDonHang.kiemTraChuyenCoDich(DA_GIAO, DA_HUY)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MayTrangThaiDonHang.kiemTraChuyenCoDich(DA_HUY, DANG_GIAO)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MayTrangThaiDonHang.kiemTraChuyenCoDich(CHO_XU_LY, DA_GIAO)).isInstanceOf(IllegalStateException.class);
    }

    // --- Tien 1 buoc: 0->1, 1->2; o 2/3 throw ---

    @Test
    void tiep_theo_tien_1_buoc() {
        assertThat(MayTrangThaiDonHang.tiepTheo(CHO_XU_LY)).isEqualTo(DANG_GIAO);
        assertThat(MayTrangThaiDonHang.tiepTheo(DANG_GIAO)).isEqualTo(DA_GIAO);
    }

    @Test
    void tiep_theo_o_trang_thai_cuoi_throw() {
        assertThatThrownBy(() -> MayTrangThaiDonHang.tiepTheo(DA_GIAO)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MayTrangThaiDonHang.tiepTheo(DA_HUY)).isInstanceOf(IllegalStateException.class);
    }

    // --- from(): null -> mac dinh, gia tri la -> throw ---

    @Test
    void trang_thai_giao_hang_from() {
        assertThat(TrangThaiGiaoHang.from(null)).isEqualTo(CHO_XU_LY);
        assertThat(TrangThaiGiaoHang.from(0)).isEqualTo(CHO_XU_LY);
        assertThat(TrangThaiGiaoHang.from(1)).isEqualTo(DANG_GIAO);
        assertThat(TrangThaiGiaoHang.from(2)).isEqualTo(DA_GIAO);
        assertThat(TrangThaiGiaoHang.from(3)).isEqualTo(DA_HUY);
        assertThatThrownBy(() -> TrangThaiGiaoHang.from(99)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void trang_thai_thanh_toan_from() {
        assertThat(TrangThaiThanhToan.from(null)).isEqualTo(TrangThaiThanhToan.CHUA_THANH_TOAN);
        assertThat(TrangThaiThanhToan.from(0)).isEqualTo(TrangThaiThanhToan.CHUA_THANH_TOAN);
        assertThat(TrangThaiThanhToan.from(1)).isEqualTo(TrangThaiThanhToan.DA_THANH_TOAN);
        assertThatThrownBy(() -> TrangThaiThanhToan.from(5)).isInstanceOf(IllegalArgumentException.class);
    }
}
