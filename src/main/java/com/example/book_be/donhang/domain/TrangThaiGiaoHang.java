package com.example.book_be.donhang.domain;

/**
 * Trang thai giao hang cua don hang. Cot DB giu kieu Integer; enum nay chi map so <-> nghia.
 * 0 = cho xu ly, 1 = dang giao, 2 = da giao, 3 = da huy.
 */
public enum TrangThaiGiaoHang {
    CHO_XU_LY(0),
    DANG_GIAO(1),
    DA_GIAO(2),
    DA_HUY(3);

    private final int giaTri;

    TrangThaiGiaoHang(int giaTri) {
        this.giaTri = giaTri;
    }

    public int getGiaTri() {
        return giaTri;
    }

    /** null (cot chua set) coi nhu CHO_XU_LY; gia tri ngoai {0,1,2,3} bao loi. */
    public static TrangThaiGiaoHang from(Integer giaTri) {
        if (giaTri == null) {
            return CHO_XU_LY;
        }
        for (TrangThaiGiaoHang trangThai : values()) {
            if (trangThai.giaTri == giaTri) {
                return trangThai;
            }
        }
        throw new IllegalArgumentException("Trang thai giao hang khong hop le: " + giaTri);
    }
}
