package com.example.book_be.nguoidung.session;

import com.example.book_be.nguoidung.domain.NguoiDung;

import java.util.List;

public record SessionPrincipal(int uid, String username, List<String> roles) {
    public static SessionPrincipal from(NguoiDung user) {
        List<String> roles = user.getDanhSachQuyen() == null
                ? List.of()
                : user.getDanhSachQuyen().stream()
                        .map(role -> role.getTenQuyen())
                        .filter(name -> name != null && !name.isBlank())
                        .distinct()
                        .sorted()
                        .toList();
        return new SessionPrincipal(user.getMaNguoiDung(), user.getTenDangNhap(), roles);
    }
}
