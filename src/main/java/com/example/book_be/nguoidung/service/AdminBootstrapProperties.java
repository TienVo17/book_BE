package com.example.book_be.nguoidung.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Cau hinh bootstrap admin. Mac dinh tat; khi bat thi moi gia tri deu bat buoc va duoc
 * validate fail-closed truoc khi cham toi database.
 */
@Component
@ConfigurationProperties(prefix = "app.admin-bootstrap")
public class AdminBootstrapProperties {

    /** Cac dinh danh seed cong khai tu V3/V4 — khong duoc tai su dung. */
    private static final Set<String> TEN_DANG_NHAP_CAM = Set.of(
            "admin", "user1", "user2", "user3", "user4", "user5");
    private static final int DO_DAI_MAT_KHAU_TOI_THIEU = 12;

    private boolean enabled;
    private String username;
    private String email;
    private String password;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public AdminBootstrapRequest yeuCauHopLe() {
        String tenDangNhap = batBuoc(username, "ADMIN_BOOTSTRAP_USERNAME");
        String email = batBuoc(this.email, "ADMIN_BOOTSTRAP_EMAIL");
        String matKhau = batBuoc(password, "ADMIN_BOOTSTRAP_PASSWORD");

        if (TEN_DANG_NHAP_CAM.contains(tenDangNhap.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(
                    "ADMIN_BOOTSTRAP_USERNAME khong duoc dung lai dinh danh seed cong khai");
        }
        if (!email.contains("@")) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_EMAIL phai la dia chi email hop le");
        }
        if (matKhau.length() < DO_DAI_MAT_KHAU_TOI_THIEU) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_PASSWORD phai co it nhat "
                    + DO_DAI_MAT_KHAU_TOI_THIEU + " ky tu");
        }
        return new AdminBootstrapRequest(tenDangNhap, email, matKhau);
    }

    private String batBuoc(String giaTri, String tenBienMoiTruong) {
        if (giaTri == null || giaTri.isBlank()) {
            throw new IllegalStateException(tenBienMoiTruong + " la bat buoc khi bat admin bootstrap");
        }
        return giaTri.trim();
    }
}
