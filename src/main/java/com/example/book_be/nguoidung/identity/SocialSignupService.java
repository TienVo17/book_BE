package com.example.book_be.nguoidung.identity;

import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.domain.Quyen;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.repository.QuyenRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Tao tai khoan moi tu mot danh tinh provider, sau khi nguoi dung da dien xong ho so.
 *
 * Callback cua provider KHONG duoc goi vao day. Neu tao `nguoi_dung` ngay tai callback thi
 * ai bo ngang o buoc dien ho so cung de lai mot tai khoan nua voi: khong dang nhap lai
 * duoc, ma van giu cho username/email. Toan bo user + quyen + identity duoc tao trong dung
 * mot transaction o buoc cuoi nay.
 */
@Service
public class SocialSignupService {
    private final NguoiDungRepository userRepository;
    private final QuyenRepository quyenRepository;
    private final AuthIdentityService identityService;

    public SocialSignupService(NguoiDungRepository userRepository,
                               QuyenRepository quyenRepository,
                               AuthIdentityService identityService) {
        this.userRepository = userRepository;
        this.quyenRepository = quyenRepository;
        this.identityService = identityService;
    }

    /**
     * @param intent ho so dang do; dia chi email chi duoc dung khi chinh ho so nay danh dau
     *               da xac minh - hoac provider khang dinh, hoac nguoi dung da nhap dung ma
     *               do ung dung gui. Facebook khong bao gio thuoc ve truong hop thu nhat.
     */
    @Transactional
    public NguoiDung complete(OAuthSignupIntent intent, CompletionRequest request) {
        ProviderIdentity claims = intent.toProviderIdentity();
        String verifiedEmail = intent.isEmailVerified() ? intent.getEmail() : null;
        if (verifiedEmail == null || !verifiedEmail.equalsIgnoreCase(request.email())) {
            // Chi dia chi da co bang chung moi dung duoc. Cho phep form gui dia chi khac
            // dong nghia voi cho phep nhan bua email cua nguoi la.
            throw new AuthIdentityException("EMAIL_NOT_VERIFIED");
        }
        if (request.username() == null || request.username().isBlank()) {
            throw new AuthIdentityException("USERNAME_REQUIRED");
        }
        if (userRepository.existsByTenDangNhap(request.username())) {
            throw new AuthIdentityException("USERNAME_TAKEN");
        }
        if (userRepository.existsByEmail(verifiedEmail)) {
            // Trung email khong bao gio tu dong lien ket. Nguoi dung phai chung minh so huu
            // tai khoan cu bang mat khau qua duong lien ket identity.
            throw new AuthIdentityException("EMAIL_TAKEN");
        }
        if (identityService.resolve(claims).linkedUser() != null) {
            throw new AuthIdentityException("IDENTITY_ALREADY_LINKED");
        }

        Quyen userRole = quyenRepository.findByTenQuyen("USER");
        if (userRole == null) {
            // Tao tai khoan khong co quyen se sinh ra mot user khong lam duoc gi va cung
            // khong sua duoc tu giao dien. Dung han con hon de lai rac.
            throw new AuthIdentityException("ROLE_UNAVAILABLE");
        }

        NguoiDung user = new NguoiDung();
        user.setTenDangNhap(request.username());
        user.setEmail(verifiedEmail);
        user.setHoDem(request.hoDem());
        user.setTen(request.ten());
        // Tai khoan social khong co mat khau. De null thay vi hash ngau nhien de duong dang
        // nhap bang mat khau dong han, khong phu thuoc vao hanh vi cua bcrypt voi chuoi la.
        user.setMatKhau(null);
        // Dia chi da duoc xac minh o buoc truoc nen khong can vong kich hoat qua email nua.
        user.setDaKichHoat(true);
        List<Quyen> roles = new ArrayList<>();
        roles.add(userRole);
        user.setDanhSachQuyen(roles);

        try {
            NguoiDung saved = userRepository.save(user);
            identityService.link(saved, claims);
            return saved;
        } catch (DataIntegrityViolationException race) {
            // Unique index moi la trong tai cuoi cung. Hai callback chay song song cho cung
            // mot tai khoan provider: ben thua cuoc phai rollback ca user lan identity.
            throw new AuthIdentityException("IDENTITY_RACE");
        }
    }

    public record CompletionRequest(String username, String email, String hoDem, String ten) {
    }
}
