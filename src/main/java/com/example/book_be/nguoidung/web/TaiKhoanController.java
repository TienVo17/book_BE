package com.example.book_be.nguoidung.web;

import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.shared.security.JwtResponse;
import com.example.book_be.shared.security.LoginRequest;
import com.example.book_be.shared.security.RateLimiter;
import com.example.book_be.nguoidung.baomat.JwtService;
import com.example.book_be.nguoidung.service.TaiKhoanService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/tai-khoan")
public class TaiKhoanController {

    /**
     * Dang nhap sai lien tiep tren cung mot tai khoan: chan do mat khau ma van cho nguoi dung
     * that thu lai. Bo dem duoc xoa ngay khi dang nhap thanh cong.
     */
    private static final int DANG_NHAP_SAI_TOI_DA = 5;
    private static final Duration DANG_NHAP_CUA_SO = Duration.ofMinutes(5);
    /**
     * Tran theo IP dat cao hon nhieu: nhieu nguoi dung that co the dung chung mot IP (NAT, VPN,
     * mang van phong). Nguong nay chi de chan spam quy mo lon, khong phai de chan nguoi dung.
     */
    private static final int DANG_NHAP_IP_TOI_DA = 300;
    /** Cac endpoint gui email: gioi han theo IP de khong bien server thanh cong cu spam. */
    private static final int GUI_EMAIL_TOI_DA = 5;
    private static final Duration GUI_EMAIL_CUA_SO = Duration.ofMinutes(15);

    @Autowired
    private TaiKhoanService taiKhoanService;
    @Autowired
    private AuthenticationManager AuthenticationManager;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private RateLimiter rateLimiter;

    @PostMapping("/dang-ky")
    public ResponseEntity<?> dangKyNguoiDung(@Validated @RequestBody NguoiDung nguoiDung,
                                             HttpServletRequest request) {
        batBuocTrongGioiHan("dang-ky:" + diaChiIp(request), GUI_EMAIL_TOI_DA, GUI_EMAIL_CUA_SO);
        return taiKhoanService.dangKyNguoiDung(nguoiDung);
    }

    @GetMapping("/kich-hoat")
    public ResponseEntity<?> kichHoatTaiKhoan(@RequestParam String email, @RequestParam String maKichHoat,
                                              HttpServletRequest request) {
        batBuocTrongGioiHan("kich-hoat:" + diaChiIp(request), GUI_EMAIL_TOI_DA, GUI_EMAIL_CUA_SO);
        return taiKhoanService.kichHoatTaiKhoan(email, maKichHoat);
    }

    @PostMapping("/dang-nhap")
    public ResponseEntity<?> dangNhap(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        String username = loginRequest.getUsername();
        String ip = diaChiIp(request);

        // Chan theo ca IP lan tai khoan: chi dem theo username thi ke tan cong doi username la
        // thoat; chi dem theo IP thi mot mang NAT chung se chan nham nguoi dung that.
        batBuocTrongGioiHan("dang-nhap-ip:" + ip, DANG_NHAP_IP_TOI_DA, DANG_NHAP_CUA_SO);

        // Chi dem lan SAI. Dang nhap dung khong tieu ton han muc, nen nguoi dung binh thuong
        // (nhieu tab, nhieu thiet bi) khong bao gio cham tran.
        String khoaSai = "dang-nhap-sai:" + username;
        try {
            Authentication authentication = AuthenticationManager.authenticate(new
                    UsernamePasswordAuthenticationToken(username, loginRequest.getPassword())
            );
            if (authentication.isAuthenticated()) {
                rateLimiter.datLai(khoaSai);
                final String jwt = jwtService.generateToken(username);
                return ResponseEntity.ok(new JwtResponse(jwt));
            }
        } catch (AuthenticationException a) {
            batBuocTrongGioiHan(khoaSai, DANG_NHAP_SAI_TOI_DA, DANG_NHAP_CUA_SO);
            // Thong bao giong nhau cho moi nguyen nhan that bai (sai mat khau, khong ton tai,
            // tai khoan bi vo hieu hoa) de khong lo tai khoan nao ton tai.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tên đăng nhập hoặc mật khẩu không chính xác.");
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Xác thực không thành công.");
    }

    // ---- Đổi mật khẩu (yêu cầu đăng nhập) ----
    @PutMapping("/doi-mat-khau")
    public ResponseEntity<?> doiMatKhau(@RequestBody Map<String, String> body) {
        String tenDangNhap = SecurityContextHolder.getContext().getAuthentication().getName();
        String matKhauCu = body.get("matKhauCu");
        String matKhauMoi = body.get("matKhauMoi");
        if (matKhauCu == null || matKhauMoi == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu thông tin mật khẩu.");
        }
        return taiKhoanService.doiMatKhau(tenDangNhap, matKhauCu, matKhauMoi);
    }

    // ---- Quên mật khẩu: gửi email reset ----
    @PostMapping("/quen-mat-khau")
    public ResponseEntity<?> quenMatKhau(@RequestBody Map<String, String> body, HttpServletRequest request) {
        batBuocTrongGioiHan("quen-mat-khau:" + diaChiIp(request), GUI_EMAIL_TOI_DA, GUI_EMAIL_CUA_SO);
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email không được để trống.");
        }
        return taiKhoanService.quenMatKhau(email);
    }

    // ---- Đặt lại mật khẩu bằng token ----
    @PostMapping("/dat-lai-mat-khau")
    public ResponseEntity<?> datLaiMatKhau(@RequestBody Map<String, String> body, HttpServletRequest request) {
        batBuocTrongGioiHan("dat-lai-mat-khau:" + diaChiIp(request), GUI_EMAIL_TOI_DA, GUI_EMAIL_CUA_SO);
        String email = body.get("email");
        String token = body.get("token");
        String matKhauMoi = body.get("matKhauMoi");
        if (email == null || token == null || matKhauMoi == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu thông tin đặt lại mật khẩu.");
        }
        return taiKhoanService.datLaiMatKhau(email, token, matKhauMoi);
    }

    private void batBuocTrongGioiHan(String khoa, int soLanToiDa, Duration cuaSo) {
        if (!rateLimiter.choPhep(khoa, soLanToiDa, cuaSo)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn đã thao tác quá nhiều lần. Vui lòng thử lại sau.");
        }
    }

    /**
     * Render/nginx dat client IP that vao X-Forwarded-For. Chi lay IP dau tien va chi khi header
     * ton tai; neu khong thi dung remote address.
     */
    private String diaChiIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
