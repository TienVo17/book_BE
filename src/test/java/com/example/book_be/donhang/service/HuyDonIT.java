package com.example.book_be.donhang.service;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.donhang.domain.ChiTietDonHang;
import com.example.book_be.donhang.domain.DonHang;
import com.example.book_be.donhang.domain.TrangThaiGiaoHang;
import com.example.book_be.donhang.repository.ChiTietDonHangRepository;
import com.example.book_be.donhang.repository.DonHangRepository;
import com.example.book_be.donhang.repository.LichSuTrangThaiDonHangRepository;
import com.example.book_be.giamgia.domain.Coupon;
import com.example.book_be.giamgia.repository.CouponRepository;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.sach.repository.SachRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test cho huy don (Testcontainers - MySQL that). KHONG chay local (DooD); compile-only, chay CI.
 * Bang chung chinh local: scripts/kiem-tra-huy-don.sh.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfig.class)
class HuyDonIT {

    @Autowired DonHangHuyService donHangHuyService;
    @Autowired DonHangRepository donHangRepository;
    @Autowired ChiTietDonHangRepository chiTietDonHangRepository;
    @Autowired SachRepository sachRepository;
    @Autowired CouponRepository couponRepository;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired LichSuTrangThaiDonHangRepository lichSuRepository;
    @Autowired PlatformTransactionManager txManager;

    private static final int MA_SACH = 1;

    private NguoiDung user(String tenDangNhap) {
        return nguoiDungRepository.findByTenDangNhap(tenDangNhap);
    }

    private void datTonKho(int giaTri) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Sach sach = sachRepository.findById((long) MA_SACH).orElseThrow();
            sach.setSoLuong(giaTri);
            sachRepository.saveAndFlush(sach);
        });
    }

    private int tonKho() {
        return sachRepository.findById((long) MA_SACH).orElseThrow().getSoLuong();
    }

    /** Tao don fixture (owner, trang thai giao/thanh toan, coupon tuy chon) voi 1 chi tiet tren MA_SACH. */
    private Long taoDon(NguoiDung owner, TrangThaiGiaoHang giaoHang, int thanhToan, Integer maCoupon, int soLuong) {
        return new TransactionTemplate(txManager).execute(status -> {
            DonHang don = new DonHang();
            don.setNgayTao(new Date());
            don.setNguoiDung(owner);
            don.setHoTen("IT Huy");
            don.setSoDienThoai("0900000000");
            don.setTongTien(100000);
            don.setTrangThaiThanhToan(thanhToan);
            don.setTrangThaiGiaoHang(giaoHang.getGiaTri());
            don.setMaCoupon(maCoupon);
            donHangRepository.saveAndFlush(don);

            Sach sach = sachRepository.findById((long) MA_SACH).orElseThrow();
            ChiTietDonHang ct = new ChiTietDonHang();
            ct.setDonHang(don);
            ct.setSach(sach);
            ct.setSoLuong(soLuong);
            ct.setGiaBan(sach.getGiaBan());
            chiTietDonHangRepository.saveAndFlush(ct);
            return (long) don.getMaDonHang();
        });
    }

    private int status(ResponseStatusException e) {
        return e.getStatusCode().value();
    }

    @Test
    void huy_hop_le_hoan_kho_va_ghi_lich_su() {
        datTonKho(5);
        Long id = taoDon(user("user1"), TrangThaiGiaoHang.CHO_XU_LY, 0, null, 2);
        donHangHuyService.huyDon(id, user("user1"), false);

        DonHang sau = donHangRepository.findById(id).orElseThrow();
        assertThat(sau.getTrangThaiGiaoHang()).isEqualTo(TrangThaiGiaoHang.DA_HUY.getGiaTri());
        assertThat(tonKho()).as("kho hoan lai 2").isEqualTo(7);
        assertThat(lichSuRepository.findByMaDonHangOrderByThoiDiemAsc(id.intValue()))
                .anyMatch(ls -> ls.getDenGiaTri() == TrangThaiGiaoHang.DA_HUY.getGiaTri());
    }

    @Test
    void user_khac_huy_thi_403() {
        datTonKho(5);
        Long id = taoDon(user("user1"), TrangThaiGiaoHang.CHO_XU_LY, 0, null, 1);
        assertThatThrownBy(() -> donHangHuyService.huyDon(id, user("user2"), false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status((ResponseStatusException) e)).isEqualTo(403));
    }

    @Test
    void user_huy_don_da_thanh_toan_thi_400() {
        datTonKho(5);
        Long id = taoDon(user("user1"), TrangThaiGiaoHang.CHO_XU_LY, 1, null, 1);
        assertThatThrownBy(() -> donHangHuyService.huyDon(id, user("user1"), false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status((ResponseStatusException) e)).isEqualTo(400));
    }

    @Test
    void user_huy_don_dang_giao_thi_409_admin_thi_ok() {
        datTonKho(5);
        Long id = taoDon(user("user1"), TrangThaiGiaoHang.DANG_GIAO, 0, null, 1);
        assertThatThrownBy(() -> donHangHuyService.huyDon(id, user("user1"), false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status((ResponseStatusException) e)).isEqualTo(409));
        // admin huy don dang giao -> OK
        donHangHuyService.huyDon(id, user("admin"), true);
        assertThat(donHangRepository.findById(id).orElseThrow().getTrangThaiGiaoHang())
                .isEqualTo(TrangThaiGiaoHang.DA_HUY.getGiaTri());
    }

    @Test
    void admin_huy_don_da_giao_thi_409() {
        datTonKho(5);
        Long id = taoDon(user("user1"), TrangThaiGiaoHang.DA_GIAO, 0, null, 1);
        assertThatThrownBy(() -> donHangHuyService.huyDon(id, user("admin"), true))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(status((ResponseStatusException) e)).isEqualTo(409));
    }

    @Test
    void hai_request_huy_dong_thoi_chi_1_thanh_cong_khong_hoan_kho_2_lan() throws Exception {
        datTonKho(5);
        Long id = taoDon(user("user1"), TrangThaiGiaoHang.CHO_XU_LY, 0, null, 2);

        int soThread = 2;
        CountDownLatch batDau = new CountDownLatch(1);
        CountDownLatch xong = new CountDownLatch(soThread);
        AtomicInteger soThanhCong = new AtomicInteger(0);
        AtomicInteger so409 = new AtomicInteger(0);
        ExecutorService pool = Executors.newFixedThreadPool(soThread);

        for (int i = 0; i < soThread; i++) {
            pool.submit(() -> {
                try {
                    batDau.await();
                    donHangHuyService.huyDon(id, user("user1"), false);
                    soThanhCong.incrementAndGet();
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode().value() == 409) so409.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    xong.countDown();
                }
            });
        }
        batDau.countDown();
        xong.await();
        pool.shutdown();

        assertThat(soThanhCong.get()).as("dung 1 huy thanh cong").isEqualTo(1);
        assertThat(so409.get()).as("request con lai 409").isEqualTo(1);
        assertThat(tonKho()).as("kho chi hoan 1 lan (5+2)").isEqualTo(7);
    }
}
