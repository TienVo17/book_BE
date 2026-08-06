package com.example.book_be.danhgia.dto;

import com.example.book_be.danhgia.domain.LyDoKhongDanhGiaDuoc;
import lombok.Data;

/**
 * Ket qua kiem tra quyen danh gia.
 *
 * <p>{@code maDonHang} chi co gia tri khi {@code coThe} la true — do la don hang se duoc
 * gan lam bang chung cho danh gia sap tao. Client KHONG duoc gui nguoc gia tri nay len:
 * backend tu tra cuu lai khi ghi, xem {@code DanhGiaServiceImpl.addReview}.
 */
@Data
public class CoTheDanhGiaResponse {
    private boolean coThe;
    private Integer maDonHang;
    private LyDoKhongDanhGiaDuoc lyDo;

    public static CoTheDanhGiaResponse duoc(int maDonHang) {
        CoTheDanhGiaResponse r = new CoTheDanhGiaResponse();
        r.coThe = true;
        r.maDonHang = maDonHang;
        return r;
    }

    public static CoTheDanhGiaResponse khong(LyDoKhongDanhGiaDuoc lyDo) {
        CoTheDanhGiaResponse r = new CoTheDanhGiaResponse();
        r.coThe = false;
        r.lyDo = lyDo;
        return r;
    }
}
