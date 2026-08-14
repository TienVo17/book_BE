package com.example.book_be.nguoidung.service;

import com.example.book_be.nguoidung.baomat.JwtService;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.session.RefreshSessionService;
import com.example.book_be.shared.config.FrontendUrlProvider;
import com.example.book_be.shared.email.EmailService;
import com.example.book_be.shared.email.HtmlEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

@Service
public class TaiKhoanService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaiKhoanService.class);

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;
    @Autowired
    private NguoiDungRepository nguoiDungRepository;
    @Autowired
    private EmailService emailService;
    @Autowired
    private FrontendUrlProvider frontendUrlProvider;
    @Autowired
    private RefreshSessionService refreshSessionService;
    @Autowired
    private JwtService jwtService;

    @Transactional
    public ResponseEntity<?> dangKyNguoiDung(NguoiDung nguoiDung) {
        // kiểm tra tên đăng nhập đã tồn tại chưa
        if (nguoiDungRepository.existsByTenDangNhap(nguoiDung.getTenDangNhap())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên đăng nhập đã tồn tại.");
        }
        if (nguoiDungRepository.existsByEmail(nguoiDung.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã tồn tại.");
        }
        // Mã hoá mật khẩu
        String encryptPassword = bCryptPasswordEncoder.encode(nguoiDung.getMatKhau());
        nguoiDung.setMatKhau(encryptPassword);

        // Gán và guửi thông tin kích hoạt
        nguoiDung.setMaKichHoat(taoMaKichHoat());
        nguoiDung.setDaKichHoat(false);

        // Neu khong co loi luu nguoi dung
        NguoiDung nguoiDung_DaDangKy = nguoiDungRepository.save(nguoiDung);

        // Tai khoan chua kich hoat se la dead-end neu email khong gui duoc, nen KHONG
        // duoc bao dang ky thanh cong. Nhung SMTP hong la loi ha tang phia tren, khong
        // phai defect cua server: tra 503 co the thu lai, va de exception rollback
        // transaction nay de khong bo lai tai khoan khong the kich hoat.
        try {
            guiEmailKichHoat(nguoiDung.getEmail(), nguoiDung.getMaKichHoat());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            LOGGER.warn("event=email_failed type=account_activation exception={}",
                    e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Không gửi được email kích hoạt, vui lòng thử lại sau.");
        }
        return ResponseEntity.ok("Đăng ký thành công");
    }

    private String taoMaKichHoat() {
        return UUID.randomUUID().toString();
    }

    private void guiEmailKichHoat(String email, String maKichHoat) {
        String subject = "Kích hoạt tài khoản của bạn tại WebBanSach";
        String url = frontendUrlProvider.activationUrl(email, maKichHoat);
        String text = "<html><body><p>Vui lòng sử dụng mã sau để kích hoạt tài khoản &lt;"
                + HtmlEncoder.encode(email) + "&gt;:</p><h1>" + HtmlEncoder.encode(maKichHoat) + "</h1>"
                + "<p>Click vào đường link để kích hoạt tài khoản:</p><a href=\""
                + HtmlEncoder.encode(url) + "\">" + HtmlEncoder.encode(url) + "</a></body></html>";

        emailService.sendEmail(email, subject, text);
    }

    public ResponseEntity<?> kichHoatTaiKhoan(String email, String maKichHoat) {
        NguoiDung nguoiDung = nguoiDungRepository.findByEmail(email);
        if (nguoiDung == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Người dùng không tồn tại");
        }
        if (nguoiDung.getDaKichHoat()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản đã được kích hoạt");
        }
        if(maKichHoat.equals(nguoiDung.getMaKichHoat())) {
            nguoiDung.setDaKichHoat(true);
            nguoiDungRepository.save(nguoiDung);
            return ResponseEntity.ok("Kích hoạt tài khoản thành công");
        }else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã kích hoạt không chính xác");
        }
    }

    @Transactional
    public AuthenticatedSession authenticateAndIssueSession(
            String tenDangNhap,
            String matKhau,
            boolean rememberMe) {
        NguoiDung nguoiDung = nguoiDungRepository
                .findByTenDangNhapForAuthWrite(tenDangNhap)
                .filter(user -> Boolean.TRUE.equals(user.getDaKichHoat()))
                .filter(user -> bCryptPasswordEncoder.matches(matKhau, user.getMatKhau()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Tên đăng nhập hoặc mật khẩu không chính xác."));
        RefreshSessionService.SessionGrant refreshGrant =
                refreshSessionService.issueIfEnabled(nguoiDung, rememberMe);
        String accessToken = jwtService.generateToken(nguoiDung);
        return new AuthenticatedSession(nguoiDung, refreshGrant, accessToken);
    }

    // ---- Đổi mật khẩu ----
    @Transactional
    public ResponseEntity<?> doiMatKhau(String tenDangNhap, String matKhauCu, String matKhauMoi) {
        NguoiDung nguoiDung = nguoiDungRepository.findByTenDangNhapForAuthWrite(tenDangNhap)
                .orElse(null);
        if (nguoiDung == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Người dùng không tồn tại");
        }
        if (!bCryptPasswordEncoder.matches(matKhauCu, nguoiDung.getMatKhau())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu cũ không chính xác");
        }
        nguoiDung.setMatKhau(bCryptPasswordEncoder.encode(matKhauMoi));
        nguoiDungRepository.save(nguoiDung);
        refreshSessionService.revokeAllByUser(nguoiDung.getMaNguoiDung());
        return ResponseEntity.ok("Đổi mật khẩu thành công");
    }

    // ---- Quên mật khẩu: tạo token và gửi email ----
    public ResponseEntity<?> quenMatKhau(String email) {
        emailService.ensureConfigured();
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
        } catch (Exception exception) {
            LOGGER.warn("event=email_failed type=password_reset exception={}",
                    exception.getClass().getSimpleName());
        }
        return ResponseEntity.ok(thongBaoThanhCong);
    }

    // ---- Đặt lại mật khẩu bằng token ----
    @Transactional
    public ResponseEntity<?> datLaiMatKhau(String email, String token, String matKhauMoi) {
        NguoiDung nguoiDung = nguoiDungRepository.findByEmailForAuthWrite(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Token không hợp lệ"));
        if (nguoiDung.getResetPasswordToken() == null || !nguoiDung.getResetPasswordToken().equals(token)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token không hợp lệ");
        }
        if (nguoiDung.getResetPasswordTokenExpiry() == null
                || nguoiDung.getResetPasswordTokenExpiry().before(new Date())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token đã hết hạn");
        }
        nguoiDung.setMatKhau(bCryptPasswordEncoder.encode(matKhauMoi));
        nguoiDung.setResetPasswordToken(null);
        nguoiDung.setResetPasswordTokenExpiry(null);
        nguoiDungRepository.save(nguoiDung);
        refreshSessionService.revokeAllByUser(nguoiDung.getMaNguoiDung());
        return ResponseEntity.ok("Đặt lại mật khẩu thành công");
    }

    public record AuthenticatedSession(
            NguoiDung user,
            RefreshSessionService.SessionGrant refreshGrant,
            String accessToken) {
    }

    // ---- Helper: gửi email reset password ----
    private void guiEmailResetPassword(String email, String token) {
        String subject = "Đặt lại mật khẩu tại WebBanSach";
        String url = frontendUrlProvider.resetPasswordUrl(email, token);
        String text = "<html><body><p>Bạn đã yêu cầu đặt lại mật khẩu cho tài khoản &lt;"
                + HtmlEncoder.encode(email) + "&gt;.</p>"
                + "<p>Vui lòng click vào đường link sau để đặt lại mật khẩu (có hiệu lực trong 10 phút):</p>"
                + "<a href=\"" + HtmlEncoder.encode(url) + "\">"
                + HtmlEncoder.encode(url) + "</a></body></html>";
        emailService.sendEmail(email, subject, text);
    }

}
