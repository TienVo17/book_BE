package com.example.book_be.portfolio;

import com.example.book_be.donhang.domain.ChiTietDonHang;
import com.example.book_be.donhang.domain.DonHang;
import com.example.book_be.donhang.domain.TrangThaiGiaoHang;
import com.example.book_be.donhang.repository.DonHangRepository;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.sach.repository.SachRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;

/**
 * Dung don hang DA_GIAO lam bang chung da mua cho fixture cua cac test danh gia.
 *
 * <p>Tu phase 2, khong con duong nao tao danh gia ma khong co don DA_GIAO chua cuon sach
 * do. Moi lop test truoc day dung danh gia thang bang repository nay deu can no.
 *
 * <p>Don fixture co {@code trangThaiThanhToan = 0} de khong bom doanh thu vao thong ke,
 * va {@code laDonDemo = false} vi chung khong phai don demo cua migration — chung la du
 * lieu test that, chi ton tai trong pham vi mot lan chay.
 */
final class DonHangDaGiaoFixture {

    private DonHangDaGiaoFixture() {
    }

    static int taoDonDaGiao(PlatformTransactionManager txManager,
                            DonHangRepository donHangRepository,
                            NguoiDungRepository nguoiDungRepository,
                            SachRepository sachRepository,
                            String tenDangNhap,
                            int maSach) {
        return new TransactionTemplate(txManager).execute(status -> {
            NguoiDung nguoiDung = nguoiDungRepository.findByTenDangNhap(tenDangNhap);
            if (nguoiDung == null) {
                throw new IllegalStateException("chua tao nguoi dung " + tenDangNhap);
            }
            Sach sach = sachRepository.findById((long) maSach)
                    .orElseThrow(() -> new IllegalStateException("khong co sach " + maSach));

            DonHang don = new DonHang();
            don.setNgayTao(new Date());
            don.setDiaChiMuaHang("Fixture");
            don.setDiaChiNhanHang("Fixture");
            don.setTongTienSanPham(sach.getGiaBan());
            don.setChiPhiGiaoHang(0);
            don.setChiPhiThanhToan(0);
            don.setTongTien(sach.getGiaBan());
            don.setHoTen("Fixture");
            don.setSoDienThoai("0000000000");
            don.setTrangThaiThanhToan(0);
            don.setTrangThaiGiaoHang(TrangThaiGiaoHang.DA_GIAO.getGiaTri());
            don.setNguoiDung(nguoiDung);
            don.setLaDonDemo(false);

            ChiTietDonHang dong = new ChiTietDonHang();
            dong.setSoLuong(1);
            dong.setGiaBan(sach.getGiaBan());
            dong.setDanhGia(false);
            dong.setSach(sach);
            dong.setDonHang(don);
            don.setDanhSachChiTietDonHang(List.of(dong));

            return donHangRepository.saveAndFlush(don).getMaDonHang();
        });
    }

    /** Don o trang thai chua giao — de kiem tra thong diep CHUA_NHAN_HANG. */
    static int taoDonChuaGiao(PlatformTransactionManager txManager,
                              DonHangRepository donHangRepository,
                              NguoiDungRepository nguoiDungRepository,
                              SachRepository sachRepository,
                              String tenDangNhap,
                              int maSach,
                              TrangThaiGiaoHang trangThai) {
        int maDonHang = taoDonDaGiao(txManager, donHangRepository, nguoiDungRepository,
                sachRepository, tenDangNhap, maSach);
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            DonHang don = donHangRepository.findById((long) maDonHang).orElseThrow();
            don.setTrangThaiGiaoHang(trangThai.getGiaTri());
            donHangRepository.saveAndFlush(don);
        });
        return maDonHang;
    }

    static void xoaDon(PlatformTransactionManager txManager,
                       DonHangRepository donHangRepository,
                       Integer maDonHang) {
        if (maDonHang == null) {
            return;
        }
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                donHangRepository.findById((long) maDonHang).ifPresent(donHangRepository::delete));
    }
}
