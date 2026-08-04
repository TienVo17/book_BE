package com.example.book_be.nguoidung.service;

import com.example.book_be.nguoidung.domain.AdminBootstrapState;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.domain.Quyen;
import com.example.book_be.nguoidung.repository.AdminBootstrapStateRepository;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.repository.QuyenRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Tao admin dau tien dung mot lan tu bien moi truong.
 *
 * Bat bien: du co bao nhieu instance khoi dong dong thoi, toi da mot admin duoc tao. Dong
 * singleton trong {@code admin_bootstrap_state} duoc khoa ghi truoc khi doc co "da su dung",
 * nen khong ton tai khe hop check-then-act giua cac transaction.
 */
@Service
public class AdminBootstrapService {

    private final AdminBootstrapStateRepository trangThaiRepository;
    private final NguoiDungRepository nguoiDungRepository;
    private final QuyenRepository quyenRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public AdminBootstrapService(AdminBootstrapStateRepository trangThaiRepository,
                                 NguoiDungRepository nguoiDungRepository,
                                 QuyenRepository quyenRepository,
                                 BCryptPasswordEncoder bCryptPasswordEncoder) {
        this.trangThaiRepository = trangThaiRepository;
        this.nguoiDungRepository = nguoiDungRepository;
        this.quyenRepository = quyenRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
    }

    /**
     * @return {@code true} neu lan goi nay tao admin; {@code false} neu bootstrap da duoc su dung.
     */
    @Transactional
    public boolean taoAdminNeuChuaSuDung(AdminBootstrapRequest yeuCau) {
        AdminBootstrapState trangThai = trangThaiRepository.khoaDeGhi(AdminBootstrapState.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Thieu dong admin_bootstrap_state; migration V10 phai chay truoc"));

        if (trangThai.isDaSuDung()) {
            return false;
        }

        Quyen quyenAdmin = quyenRepository.findByTenQuyen("ADMIN");
        if (quyenAdmin == null) {
            throw new IllegalStateException("Thieu quyen ADMIN; migration V2 phai chay truoc");
        }

        NguoiDung admin = new NguoiDung();
        admin.setHoDem("Quan Tri");
        admin.setTen("He Thong");
        admin.setTenDangNhap(yeuCau.tenDangNhap());
        admin.setMatKhau(bCryptPasswordEncoder.encode(yeuCau.matKhau()));
        admin.setGioiTinh('X');
        admin.setEmail(yeuCau.email());
        admin.setDaKichHoat(true);
        admin.setDanhSachQuyen(List.of(quyenAdmin));

        try {
            nguoiDungRepository.saveAndFlush(admin);
        } catch (DataIntegrityViolationException trungDinhDanh) {
            // Rang buoc unique tu V10: username/email da thuoc ve mot tai khoan khac.
            throw new IllegalStateException(
                    "ADMIN_BOOTSTRAP_USERNAME hoac ADMIN_BOOTSTRAP_EMAIL da ton tai; chon dinh danh khac",
                    trungDinhDanh);
        }

        trangThai.danhDauDaSuDung(yeuCau.tenDangNhap());
        trangThaiRepository.saveAndFlush(trangThai);
        return true;
    }
}
