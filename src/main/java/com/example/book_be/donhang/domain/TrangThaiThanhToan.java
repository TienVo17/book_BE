package com.example.book_be.donhang.domain;

/**
 * Trang thai thanh toan cua don hang. Cot DB giu kieu Integer.
 * 0 = chua thanh toan, 1 = da thanh toan.
 */
public enum TrangThaiThanhToan {
    CHUA_THANH_TOAN(0),
    DA_THANH_TOAN(1);

    private final int giaTri;

    TrangThaiThanhToan(int giaTri) {
        this.giaTri = giaTri;
    }

    public int getGiaTri() {
        return giaTri;
    }

    /** null (cot chua set) coi nhu CHUA_THANH_TOAN; gia tri ngoai {0,1} bao loi. */
    public static TrangThaiThanhToan from(Integer giaTri) {
        if (giaTri == null) {
            return CHUA_THANH_TOAN;
        }
        for (TrangThaiThanhToan trangThai : values()) {
            if (trangThai.giaTri == giaTri) {
                return trangThai;
            }
        }
        throw new IllegalArgumentException("Trang thai thanh toan khong hop le: " + giaTri);
    }
}
