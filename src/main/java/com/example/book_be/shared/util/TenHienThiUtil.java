package com.example.book_be.shared.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Che ten nguoi danh gia truoc khi ra duong cong khai: {@code "Nguyễn Văn An"} thanh
 * {@code "Nguyễn V. A."}.
 *
 * <p>Che o BACKEND chu khong phai o giao dien. Che o frontend thi ten day du van nam
 * nguyen trong response va bat ky ai mo DevTools cung doc duoc — do la trang tri, khong
 * phai bao ve.
 *
 * <p>Dat o {@code shared/util/} cho khop {@code SlugUtil}: repo chi co mot cho cho loai
 * helper thuan tuy nay.
 */
public final class TenHienThiUtil {

    /** Hien khi khong con gi de hien. Khong bao gio tra ve chuoi rong. */
    public static final String TEN_AN_DANH = "Khách hàng";

    private TenHienThiUtil() {
    }

    /**
     * Giu nguyen tu dau, rut gon moi tu con lai thanh chu cai dau kem dau cham.
     *
     * <p>Giu tu dau vi ho la phan it dac hieu nhat cua mot cai ten tieng Viet — "Nguyễn"
     * khong chi ra ai ca. Rut gon phan con lai vi ten dem va ten goi moi la phan nhan dang.
     */
    public static String che(String hoDem, String ten) {
        List<String> tu = new ArrayList<>();
        for (String phan : ((hoDem == null ? "" : hoDem) + " " + (ten == null ? "" : ten)).split("\\s+")) {
            if (!phan.isBlank()) {
                tu.add(phan);
            }
        }
        if (tu.isEmpty()) {
            return TEN_AN_DANH;
        }
        if (tu.size() == 1) {
            return tu.get(0);
        }

        StringBuilder ketQua = new StringBuilder(tu.get(0));
        for (int i = 1; i < tu.size(); i++) {
            ketQua.append(' ')
                    .append(tu.get(i).substring(0, 1).toUpperCase(Locale.ROOT))
                    .append('.');
        }
        return ketQua.toString();
    }
}
