package com.example.book_be.controller;

import com.example.book_be.entity.NguoiDung;
import com.example.book_be.entity.ThongBao;
import com.example.book_be.services.NguoiDungService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "http://localhost:3000/")
@RequestMapping("/api/nguoi-dung")
public class NguoiDungController {

    @Autowired
    private NguoiDungService nguoiDungService;

    // ---- Lấy hồ sơ người dùng hiện tại ----
    @GetMapping("/ho-so")
    public ResponseEntity<?> getHoSo() {
        String tenDangNhap = SecurityContextHolder.getContext().getAuthentication().getName();
        NguoiDung nguoiDung = nguoiDungService.getHoSo(tenDangNhap);
        if (nguoiDung == null) {
            return ResponseEntity.badRequest().body(new ThongBao("Người dùng không tồn tại"));
        }
        // Ẩn các trường nhạy cảm trước khi trả về client
        nguoiDung.setMatKhau(null);
        nguoiDung.setMaKichHoat(null);
        nguoiDung.setResetPasswordToken(null);
        nguoiDung.setResetPasswordTokenExpiry(null);
        return ResponseEntity.ok(nguoiDung);
    }

    // ---- Cập nhật hồ sơ người dùng hiện tại ----
    @PutMapping("/cap-nhat-ho-so")
    public ResponseEntity<?> capNhatHoSo(@RequestBody NguoiDung updates) {
        String tenDangNhap = SecurityContextHolder.getContext().getAuthentication().getName();
        NguoiDung nguoiDung = nguoiDungService.capNhatHoSo(tenDangNhap, updates);
        if (nguoiDung == null) {
            return ResponseEntity.badRequest().body(new ThongBao("Người dùng không tồn tại"));
        }
        // Ẩn các trường nhạy cảm trước khi trả về client
        nguoiDung.setMatKhau(null);
        nguoiDung.setMaKichHoat(null);
        nguoiDung.setResetPasswordToken(null);
        nguoiDung.setResetPasswordTokenExpiry(null);
        return ResponseEntity.ok(nguoiDung);
    }
}
