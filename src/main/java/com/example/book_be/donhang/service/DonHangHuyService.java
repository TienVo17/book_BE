package com.example.book_be.donhang.service;

import com.example.book_be.donhang.domain.ChiTietDonHang;
import com.example.book_be.donhang.domain.DonHang;
import com.example.book_be.donhang.domain.TrangThaiGiaoHang;
import com.example.book_be.donhang.repository.DonHangRepository;
import com.example.book_be.giamgia.repository.CouponRepository;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.sach.repository.SachRepository;
import com.example.book_be.shared.email.EmailService;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

/**
 * Huy don hang: hoan kho + hoan luot coupon + email.
 *
 * Thu tu bat buoc: chuyen trang thai (co @Version) TRUOC khi dung kho/coupon -> request thu 2 huy
 * cung don nhan optimistic lock 409, KHONG hoan kho/coupon 2 lan. Tat ca trong 1 transaction.
 * Email gui qua afterCommit synchronization -> chi gui SAU khi commit thanh cong, NGOAI transaction
 * (SMTP loi KHONG rollback huy don).
 */
@Service
public class DonHangHuyService {

    private static final String FROM_EMAIL = "tienvovan917@gmail.com";

    private final DonHangRepository donHangRepository;
    private final DonHangTrangThaiService donHangTrangThaiService;
    private final SachRepository sachRepository;
    private final CouponRepository couponRepository;
    private final EmailService emailService;

    public DonHangHuyService(DonHangRepository donHangRepository,
                             DonHangTrangThaiService donHangTrangThaiService,
                             SachRepository sachRepository,
                             CouponRepository couponRepository,
                             EmailService emailService) {
        this.donHangRepository = donHangRepository;
        this.donHangTrangThaiService = donHangTrangThaiService;
        this.sachRepository = sachRepository;
        this.couponRepository = couponRepository;
        this.emailService = emailService;
    }

    @Transactional
    public DonHang huyDon(Long maDonHang, NguoiDung nguoiThucHien, boolean laAdmin) {
        DonHang don = donHangRepository.findById(maDonHang)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Đơn hàng không tồn tại."));

        // So sanh int getMaNguoiDung() (khong so sanh tham chieu); don nguoiDung=null -> chi admin thao tac duoc.
        boolean chuSoHuu = don.getNguoiDung() != null
                && don.getNguoiDung().getMaNguoiDung() == nguoiThucHien.getMaNguoiDung();
        if (!laAdmin && !chuSoHuu) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền hủy đơn hàng này.");
        }
        if (!laAdmin && don.getTrangThaiThanhToan() != null && don.getTrangThaiThanhToan() == 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Đơn đã thanh toán online, vui lòng liên hệ admin để hủy.");
        }

        TrangThaiGiaoHang trangThai = TrangThaiGiaoHang.from(don.getTrangThaiGiaoHang());
        boolean choPhep = laAdmin
                ? (trangThai == TrangThaiGiaoHang.CHO_XU_LY || trangThai == TrangThaiGiaoHang.DANG_GIAO)
                : (trangThai == TrangThaiGiaoHang.CHO_XU_LY);
        if (!choPhep) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng không thể hủy ở trạng thái hiện tại.");
        }

        // Buoc nay TRUOC: @Version chan double-cancel (request thu 2 -> 409, khong chay toi hoan kho/coupon).
        donHangTrangThaiService.chuyenTrangThaiGiaoHang(don, TrangThaiGiaoHang.DA_HUY, nguoiThucHien.getTenDangNhap());

        List<ChiTietDonHang> chiTiets = don.getDanhSachChiTietDonHang();
        if (chiTiets != null) {
            chiTiets.stream()
                    .filter(ct -> ct.getSach() != null)
                    // Sap theo maSach (nhat quan voi thu tu tru kho phase 3) -> tranh deadlock.
                    .sorted(Comparator.comparingInt(ct -> ct.getSach().getMaSach()))
                    .forEach(ct -> sachRepository.hoanKho(ct.getSach().getMaSach(), ct.getSoLuong()));
        }

        if (don.getMaCoupon() != null) {
            couponRepository.giamLuotSuDung(don.getMaCoupon());
        }

        guiEmailSauCommit(don);
        return don;
    }

    /**
     * Dang ky gui email SAU khi transaction commit (ngoai transaction). Capture gia tri truoc khi
     * persistence context dong. SMTP loi chi log, KHONG anh huong huy don da commit.
     */
    private void guiEmailSauCommit(DonHang don) {
        NguoiDung chuDon = don.getNguoiDung();
        if (chuDon == null || chuDon.getEmail() == null || chuDon.getEmail().isBlank()) {
            return;
        }
        final int maDon = don.getMaDonHang();
        final String toEmail = chuDon.getEmail();
        final String tenKhach = (chuDon.getHoDem() == null ? "" : chuDon.getHoDem() + " ") + chuDon.getTen();

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            guiEmail(maDon, toEmail, tenKhach);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                guiEmail(maDon, toEmail, tenKhach);
            }
        });
    }

    private void guiEmail(int maDon, String toEmail, String tenKhach) {
        try {
            String noiDung = "<p>Chào " + tenKhach + ",</p>"
                    + "<p>Đơn hàng <b>#" + maDon + "</b> của bạn đã được hủy thành công.</p>"
                    + "<p>Nếu bạn đã thanh toán, chúng tôi sẽ liên hệ để hoàn tiền. Trân trọng!</p>";
            emailService.sendEmail(FROM_EMAIL, toEmail, "Đơn hàng #" + maDon + " đã được hủy", noiDung);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
