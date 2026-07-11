package com.example.book_be.donhang.domain;

/**
 * May trang thai (thuan, khong phu thuoc Spring) cho trang thai giao hang.
 *
 * Hai co che chuyen trang thai rieng biet:
 * <ul>
 *   <li>{@link #kiemTraChuyenCoDich} - chuyen co dich tuong minh (dung boi VNPay callback, huy don).
 *       Hop le: 0->1, 0->3, 1->3. Idempotent (target == hien tai) do service xu ly TRUOC khi goi ham nay.</li>
 *   <li>{@link #tiepTheo} - tien 1 buoc, khong co dich (dung boi endpoint admin cap-nhat-trang-thai-giao-hang).
 *       0->1, 1->2; o 2 hoac 3 -> bao loi vi da o trang thai cuoi.</li>
 * </ul>
 */
public final class MayTrangThaiDonHang {

    private MayTrangThaiDonHang() {
    }

    /**
     * Kiem tra mot chuyen trang thai co dich la hop le. Chi goi khi {@code den != tu}
     * (service tu xu ly idempotent no-op khi target == hien tai).
     * Hop le: CHO_XU_LY->DANG_GIAO, CHO_XU_LY->DA_HUY, DANG_GIAO->DA_HUY.
     *
     * @throws IllegalStateException neu chuyen khong hop le
     */
    public static void kiemTraChuyenCoDich(TrangThaiGiaoHang tu, TrangThaiGiaoHang den) {
        boolean hopLe =
                (tu == TrangThaiGiaoHang.CHO_XU_LY && den == TrangThaiGiaoHang.DANG_GIAO)
                        || (tu == TrangThaiGiaoHang.CHO_XU_LY && den == TrangThaiGiaoHang.DA_HUY)
                        || (tu == TrangThaiGiaoHang.DANG_GIAO && den == TrangThaiGiaoHang.DA_HUY);
        if (!hopLe) {
            throw new IllegalStateException(
                    "Khong the chuyen trang thai giao hang tu " + tu + " sang " + den);
        }
    }

    /**
     * Tra ve trang thai ke tiep khi tien 1 buoc: CHO_XU_LY->DANG_GIAO, DANG_GIAO->DA_GIAO.
     *
     * @throws IllegalStateException neu da o trang thai cuoi (DA_GIAO hoac DA_HUY)
     */
    public static TrangThaiGiaoHang tiepTheo(TrangThaiGiaoHang tu) {
        switch (tu) {
            case CHO_XU_LY:
                return TrangThaiGiaoHang.DANG_GIAO;
            case DANG_GIAO:
                return TrangThaiGiaoHang.DA_GIAO;
            default:
                throw new IllegalStateException("Don da o trang thai cuoi, khong the tien tiep: " + tu);
        }
    }
}
