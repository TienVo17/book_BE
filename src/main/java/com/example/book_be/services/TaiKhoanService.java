package com.example.book_be.services;

import com.example.book_be.dao.NguoiDungRepository;
import com.example.book_be.entity.NguoiDung;
import com.example.book_be.entity.ThongBao;
import com.example.book_be.services.email.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

@Service
public class TaiKhoanService {
    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;
    @Autowired
    private NguoiDungRepository nguoiDungRepository;
    @Autowired
    private EmailService emailService;

    public ResponseEntity<?> dangKyNguoiDung(NguoiDung nguoiDung) {
        // kiểm tra tên đăng nhập đã tồn tại chưa
        if (nguoiDungRepository.existsByTenDangNhap(nguoiDung.getTenDangNhap())) {
            return ResponseEntity.badRequest().body(new ThongBao("Tên đăng nhập đã tồn tại"));
        }
        if (nguoiDungRepository.existsByEmail(nguoiDung.getEmail())) {
            return ResponseEntity.badRequest().body(new ThongBao("Email đã tồn tại"));
        }
        // Mã hoá mật khẩu
        String encryptPassword = bCryptPasswordEncoder.encode(nguoiDung.getMatKhau());
        nguoiDung.setMatKhau(encryptPassword);

        // Gán và guửi thông tin kích hoạt
        nguoiDung.setMaKichHoat(taoMaKichHoat());
        nguoiDung.setDaKichHoat(false);

        // Neu khong co loi luu nguoi dung
        NguoiDung nguoiDung_DaDangKy = nguoiDungRepository.save(nguoiDung);

        // gửi email kích hoạt (không fail nếu gửi email lỗi)
        try {
            guiEmailKichHoat(nguoiDung.getEmail(), nguoiDung.getMaKichHoat());
        } catch (Exception e) {
            // Log lỗi nhưng không fail registration
        }
        return ResponseEntity.ok("Đăng ký thành công");
    }

    private String taoMaKichHoat() {
        return UUID.randomUUID().toString();
    }

    private void guiEmailKichHoat(String email, String maKichHoat) {
        String subject = "Kích hoạt tài khoản của bạn tại WebBanSach";
        String text = "Vui lòng sử dụng mã sau để kich hoạt cho tài khoản <" + email + ">:<html><body><br/><h1>" + maKichHoat + "</h1></body></html>";
        text += "<br/> Click vào đường link để kích hoạt tài khoản: ";
        String url = "http://localhost:3000/kich-hoat/" + email + "/" + maKichHoat;
        text += ("<br/> <a href=" + url + ">" + url + "</a> ");

        emailService.sendEmail("tienvovan917@gmail.com", email, subject, text);
    }

    public ResponseEntity<?> kichHoatTaiKHoan(String email, String maKichHoat) {
        NguoiDung nguoiDung = nguoiDungRepository.findByEmail(email);

        if (nguoiDung == null) {
            return ResponseEntity.badRequest().body(new ThongBao("Người dùng không tồn tại!"));
        }

        if (nguoiDung.getDaKichHoat()) {
            return ResponseEntity.badRequest().body(new ThongBao("Tài khoản đã được kích hoạt!"));
        }

        if (maKichHoat.equals(nguoiDung.getMaKichHoat())) {
            nguoiDung.setDaKichHoat(true);
            nguoiDungRepository.save(nguoiDung);
            return ResponseEntity.ok("Kích hoạt tài khoản thành công!");
        } else {
            return ResponseEntity.badRequest().body(new ThongBao("Mã kích hoạt không chính xác!"));
        }
    }

    public ResponseEntity<?> kichHoatTaiKhoan(String email, String maKichHoat) {
        NguoiDung nguoiDung = nguoiDungRepository.findByEmail(email);
        if (nguoiDung == null) {
            return ResponseEntity.badRequest().body(new ThongBao("Người dùng không tồn tại"));
        }
        if (nguoiDung.getDaKichHoat()) {
            return ResponseEntity.badRequest().body(new ThongBao("Tài khoản đã được kích hoạt"));
        }
        if(maKichHoat.equals(nguoiDung.getMaKichHoat())) {
            nguoiDung.setDaKichHoat(true);
            nguoiDungRepository.save(nguoiDung);
            return ResponseEntity.ok("Kích hoạt tài khoản thành công");
        }else {
            return ResponseEntity.badRequest().body(new ThongBao("Mã kích hoạt không chính xác"));
        }
    }

    // ---- Đổi mật khẩu ----
    public ResponseEntity<?> doiMatKhau(String tenDangNhap, String matKhauCu, String matKhauMoi) {
        NguoiDung nguoiDung = nguoiDungRepository.findByTenDangNhap(tenDangNhap);
        if (nguoiDung == null) {
            return ResponseEntity.badRequest().body(new ThongBao("Người dùng không tồn tại"));
        }
        if (!bCryptPasswordEncoder.matches(matKhauCu, nguoiDung.getMatKhau())) {
            return ResponseEntity.badRequest().body(new ThongBao("Mật khẩu cũ không chính xác"));
        }
        nguoiDung.setMatKhau(bCryptPasswordEncoder.encode(matKhauMoi));
        nguoiDungRepository.save(nguoiDung);
        return ResponseEntity.ok("Đổi mật khẩu thành công");
    }

    // ---- Quên mật khẩu: tạo token và gửi email ----
    public ResponseEntity<?> quenMatKhau(String email) {
        String thongBaoThanhCong = "Nếu email tồn tại, hệ thống đã gửi hướng dẫn đặt lại mật khẩu.";
        NguoiDung nguoiDung = nguoiDungRepository.findByEmail(email);
        if (nguoiDung == null) {
            return ResponseEntity.ok(thongBaoThanhCong);
        }
        String token = UUID.randomUUID().toString();
        // Hết hạn sau 10 phút
        Date expiry = new Date(System.currentTimeMillis() + 10 * 60 * 1000L);
        nguoiDung.setResetPasswordToken(token);
        nguoiDung.setResetPasswordTokenExpiry(expiry);
        nguoiDungRepository.save(nguoiDung);
        try {
            guiEmailResetPassword(email, token);
        } catch (Exception e) {
            // Log lỗi nhưng không fail
        }
        return ResponseEntity.ok(thongBaoThanhCong);
    }

    // ---- Đặt lại mật khẩu bằng token ----
    public ResponseEntity<?> datLaiMatKhau(String email, String token, String matKhauMoi) {
        NguoiDung nguoiDung = nguoiDungRepository.findByEmail(email);
        if (nguoiDung == null) {
            return ResponseEntity.badRequest().body(new ThongBao("Token không hợp lệ"));
        }
        if (nguoiDung.getResetPasswordToken() == null || !nguoiDung.getResetPasswordToken().equals(token)) {
            return ResponseEntity.badRequest().body(new ThongBao("Token không hợp lệ"));
        }
        if (nguoiDung.getResetPasswordTokenExpiry() == null
                || nguoiDung.getResetPasswordTokenExpiry().before(new Date())) {
            return ResponseEntity.badRequest().body(new ThongBao("Token đã hết hạn"));
        }
        nguoiDung.setMatKhau(bCryptPasswordEncoder.encode(matKhauMoi));
        nguoiDung.setResetPasswordToken(null);
        nguoiDung.setResetPasswordTokenExpiry(null);
        nguoiDungRepository.save(nguoiDung);
        return ResponseEntity.ok("Đặt lại mật khẩu thành công");
    }

    // ---- Helper: gửi email reset password ----
    private void guiEmailResetPassword(String email, String token) {
        String subject = "Đặt lại mật khẩu tại WebBanSach";
        String url = "http://localhost:3000/dat-lai-mat-khau/" + email + "/" + token;
        String text = "Bạn đã yêu cầu đặt lại mật khẩu cho tài khoản <" + email + ">."
                + "<html><body><br/>Vui lòng click vào đường link sau để đặt lại mật khẩu (có hiệu lực trong 10 phút):"
                + "<br/><a href=" + url + ">" + url + "</a></body></html>";
        emailService.sendEmail("tienvovan917@gmail.com", email, subject, text);
    }

    public static void main(String[] args) {
// Tạo đối tượng BCryptPasswordEncoder
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        // Mật khẩu bạn muốn mã hóa
        String rawPassword = "12345678@";

        // Mã hóa mật khẩu
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // In kết quả
        System.out.println("Encoded Password: " + encodedPassword);
    }

}
