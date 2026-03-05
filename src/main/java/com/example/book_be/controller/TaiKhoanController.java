package com.example.book_be.controller;

import com.example.book_be.entity.NguoiDung;
import com.example.book_be.security.JwtResponse;
import com.example.book_be.security.LoginRequest;
import com.example.book_be.services.JWT.JwtService;
import com.example.book_be.services.TaiKhoanService;
import com.example.book_be.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@CrossOrigin(origins = "http://localhost:3000/")
@RequestMapping("/tai-khoan")
public class TaiKhoanController {
    @Autowired
    private TaiKhoanService taiKhoanService;
    @Autowired
    private AuthenticationManager AuthenticationManager;

    private UserService userService;
    @Autowired
    private JwtService jwtService;

    // Rate limiting: theo dõi số lần đăng nhập sai theo username
    // long[0] = số lần sai, long[1] = thời điểm lần sai cuối cùng (ms)
    private final Map<String, long[]> loginAttempts = new ConcurrentHashMap<>();
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_TIME_MS = 5 * 60 * 1000; // 5 phút

    @PostMapping("/dang-ky")
    public ResponseEntity<?> dangKyNguoiDung(@Validated @RequestBody NguoiDung nguoiDung) {
        ResponseEntity<?> response = taiKhoanService.dangKyNguoiDung(nguoiDung);
        return response;
    }

    @GetMapping("/kich-hoat")
    public ResponseEntity<?> kichHoatTaiKhoan(@RequestParam String email, @RequestParam String maKichHoat) {
        ResponseEntity<?> response = taiKhoanService.kichHoatTaiKhoan(email, maKichHoat);
        return response;
    }

    @PostMapping("/dang-nhap")
    public ResponseEntity<?> dangNhap(@RequestBody LoginRequest loginRequest) {
        String username = loginRequest.getUsername();

        // Kiểm tra rate limit - nếu đăng nhập sai quá nhiều thì khóa tạm thời
        long[] attemptData = loginAttempts.get(username);
        if (attemptData != null && attemptData[0] >= MAX_ATTEMPTS) {
            long lockUntil = attemptData[1] + LOCK_TIME_MS;
            if (System.currentTimeMillis() < lockUntil) {
                long remainingSec = (lockUntil - System.currentTimeMillis()) / 1000;
                return ResponseEntity.status(429)
                        .body("Tài khoản tạm khóa do đăng nhập sai quá " + MAX_ATTEMPTS + " lần. Vui lòng thử lại sau " + remainingSec + " giây.");
            }
            // Hết thời gian khóa, reset
            loginAttempts.remove(username);
        }

        try {
            Authentication authentication = AuthenticationManager.authenticate(new
                    UsernamePasswordAuthenticationToken(username, loginRequest.getPassword())
            );
            if (authentication.isAuthenticated()) {
                // Đăng nhập thành công, xóa record đếm lần sai
                loginAttempts.remove(username);
                final String jwt = jwtService.generateToken(username);
                return ResponseEntity.ok(new JwtResponse(jwt));
            }
        } catch (AuthenticationException a) {
            // Đăng nhập thất bại, tăng bộ đếm
            long now = System.currentTimeMillis();
            loginAttempts.merge(username, new long[]{1, now},
                    (old, v) -> new long[]{old[0] + 1, now});
            return ResponseEntity.badRequest().body("Tên đăng nhập hoặc mật khẩu không chính xác. ");
        }
        return ResponseEntity.badRequest().body("Xác thực không thành công.");
    }
}
