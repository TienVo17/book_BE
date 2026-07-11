package com.example.book_be.donhang.service;

import com.example.book_be.donhang.domain.DonHang;
import com.example.book_be.donhang.domain.LichSuTrangThaiDonHang;
import com.example.book_be.donhang.domain.MayTrangThaiDonHang;
import com.example.book_be.donhang.domain.TrangThaiGiaoHang;
import com.example.book_be.donhang.domain.TrangThaiThanhToan;
import com.example.book_be.donhang.repository.DonHangRepository;
import com.example.book_be.donhang.repository.LichSuTrangThaiDonHangRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;

/**
 * Diem duy nhat thay doi trang thai don hang. Moi chuyen hop le ghi 1 dong lich su.
 * saveAndFlush() ep version check ngay trong try/catch de bat optimistic lock (chong double-cancel).
 */
@Service
public class DonHangTrangThaiService {

    private final DonHangRepository donHangRepository;
    private final LichSuTrangThaiDonHangRepository lichSuRepository;

    public DonHangTrangThaiService(DonHangRepository donHangRepository,
                                   LichSuTrangThaiDonHangRepository lichSuRepository) {
        this.donHangRepository = donHangRepository;
        this.lichSuRepository = lichSuRepository;
    }

    /**
     * Chuyen trang thai giao hang toi dich tuong minh. Idempotent no-op khi target == hien tai
     * (an toan cho VNPay callback goi lai). Hop le: 0->1, 0->3, 1->3; nguoc lai 409.
     */
    @Transactional
    public DonHang chuyenTrangThaiGiaoHang(DonHang don, TrangThaiGiaoHang target, String nguoiThucHien) {
        TrangThaiGiaoHang hienTai = TrangThaiGiaoHang.from(don.getTrangThaiGiaoHang());
        if (hienTai == target) {
            return don;
        }
        try {
            MayTrangThaiDonHang.kiemTraChuyenCoDich(hienTai, target);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        don.setTrangThaiGiaoHang(target.getGiaTri());
        luu(don);
        ghiLichSu(don.getMaDonHang(), LichSuTrangThaiDonHang.TRUONG_GIAO_HANG,
                hienTai.getGiaTri(), target.getGiaTri(), nguoiThucHien);
        return don;
    }

    /**
     * Tien 1 buoc trang thai giao hang (dung boi endpoint admin cap-nhat-trang-thai-giao-hang):
     * 0->1, 1->2. O 2 hoac 3 -> 409 "da o trang thai cuoi".
     */
    @Transactional
    public DonHang chuyenTrangThaiTiepTheo(DonHang don, String nguoiThucHien) {
        // VNPAY tra truoc: don VNPAY chua thanh toan KHONG duoc giao (COD/null-method thi cho qua).
        boolean laVnpay = "VNPAY".equalsIgnoreCase(don.getPhuongThucThanhToan());
        if (laVnpay && TrangThaiThanhToan.from(don.getTrangThaiThanhToan()) != TrangThaiThanhToan.DA_THANH_TOAN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Đơn VNPAY chưa thanh toán, không thể cập nhật giao hàng.");
        }

        TrangThaiGiaoHang hienTai = TrangThaiGiaoHang.from(don.getTrangThaiGiaoHang());
        TrangThaiGiaoHang tiep;
        try {
            tiep = MayTrangThaiDonHang.tiepTheo(hienTai);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Don da o trang thai cuoi.");
        }
        don.setTrangThaiGiaoHang(tiep.getGiaTri());
        luu(don);
        ghiLichSu(don.getMaDonHang(), LichSuTrangThaiDonHang.TRUONG_GIAO_HANG,
                hienTai.getGiaTri(), tiep.getGiaTri(), nguoiThucHien);

        // COD giao thanh cong (DA_GIAO) = da thu tien mat -> danh dau da thanh toan (dong bo doanh thu).
        // null-method (them-don-hang-moi) coi nhu COD, khop isCashOnDelivery. Chay trong cung transaction.
        if (tiep == TrangThaiGiaoHang.DA_GIAO && !laVnpay) {
            chuyenTrangThaiThanhToan(don, TrangThaiThanhToan.DA_THANH_TOAN, nguoiThucHien);
        }
        return don;
    }

    /**
     * Chuyen trang thai thanh toan. Idempotent no-op khi target == hien tai; hop le mot chieu 0->1.
     */
    @Transactional
    public DonHang chuyenTrangThaiThanhToan(DonHang don, TrangThaiThanhToan target, String nguoiThucHien) {
        TrangThaiThanhToan hienTai = TrangThaiThanhToan.from(don.getTrangThaiThanhToan());
        if (hienTai == target) {
            return don;
        }
        if (!(hienTai == TrangThaiThanhToan.CHUA_THANH_TOAN && target == TrangThaiThanhToan.DA_THANH_TOAN)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Khong the chuyen trang thai thanh toan tu " + hienTai + " sang " + target);
        }
        don.setTrangThaiThanhToan(target.getGiaTri());
        luu(don);
        ghiLichSu(don.getMaDonHang(), LichSuTrangThaiDonHang.TRUONG_THANH_TOAN,
                hienTai.getGiaTri(), target.getGiaTri(), nguoiThucHien);
        return don;
    }

    private void luu(DonHang don) {
        try {
            donHangRepository.saveAndFlush(don);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Don hang dang duoc xu ly, vui long thu lai.");
        }
    }

    private void ghiLichSu(int maDonHang, String truong, Integer tuGiaTri, int denGiaTri, String nguoiThucHien) {
        LichSuTrangThaiDonHang lichSu = new LichSuTrangThaiDonHang();
        lichSu.setMaDonHang(maDonHang);
        lichSu.setTruong(truong);
        lichSu.setTuGiaTri(tuGiaTri);
        lichSu.setDenGiaTri(denGiaTri);
        lichSu.setNguoiThucHien(nguoiThucHien);
        lichSu.setThoiDiem(new Date());
        lichSuRepository.save(lichSu);
    }
}
