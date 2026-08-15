package com.example.book_be.nguoidung.identity;

import java.util.Set;

/**
 * Loc duong dan quay ve sau khi dang nhap qua provider.
 *
 * Dung allowlist chu khong phai blacklist: dem cac dang doc hai bao gio cung thieu mot dang,
 * con danh sach cho phep thi sai ve phia tu choi. Duong dan la khong duoc chap nhan se ve
 * trang chu, khong bao gio chuyen huong ra ngoai.
 */
public final class SocialReturnPath {
    private static final String HOME = "/";

    private static final Set<String> ALLOWED = Set.of(
            "/",
            "/tai-khoan/oauth/ket-qua",
            "/gio-hang",
            "/thanh-toan",
            "/profile",
            "/yeu-thich",
            "/order");

    private SocialReturnPath() {
    }

    public static String sanitize(String requested) {
        if (requested == null || requested.isBlank()) {
            return HOME;
        }
        String value = requested.trim();

        // Backslash duoc mot so trinh duyet coi nhu dau phan cach duong dan, nen "/\host"
        // van dieu huong ra ngoai du trong giong duong dan tuong doi.
        if (value.indexOf('\\') >= 0) {
            return HOME;
        }
        // "//host" va "https:/host" deu roi khoi origin nay du khong co dang tuyet doi day du.
        if (!value.startsWith("/") || value.startsWith("//")) {
            return HOME;
        }
        int fragment = value.indexOf('#');
        if (fragment >= 0) {
            value = value.substring(0, fragment);
        }
        // Chan traversal ca o dang thuong lan dang da ma hoa phan tram, vi tang phia sau co
        // the giai ma mot lan nua truoc khi dung.
        String lowered = value.toLowerCase();
        if (lowered.contains("..") || lowered.contains("%2e") || lowered.contains("%2f")
                || lowered.contains("%5c")) {
            return HOME;
        }
        int query = value.indexOf('?');
        String path = query >= 0 ? value.substring(0, query) : value;
        return ALLOWED.contains(path) ? value : HOME;
    }
}
