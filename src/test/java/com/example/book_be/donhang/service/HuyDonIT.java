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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test cho huy don tren MySQL Testcontainers.
 * Moi worker concurrency co transaction rieng va timeout de khong nuot loi.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
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
    private static final long TIMEOUT_SECONDS = 20;

    private final List<Long> donHangFixtures = new ArrayList<>();
    private Integer tonKhoBanDau;

    @AfterEach
    void cleanupFixtures() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            for (Long maDonHang : donHangFixtures) {
                lichSuRepository.deleteAll(lichSuRepository.findByMaDonHangOrderByThoiDiemAsc(maDonHang.intValue()));
                List<ChiTietDonHang> chiTiets = chiTietDonHangRepository.findAll().stream()
                        .filter(item -> item.getDonHang() != null
                                && item.getDonHang().getMaDonHang() == maDonHang.intValue())
                        .toList();
                chiTietDonHangRepository.deleteAll(chiTiets);
                if (donHangRepository.existsById(maDonHang)) {
                    donHangRepository.deleteById(maDonHang);
                }
            }
        });
        if (tonKhoBanDau != null) {
            int original = tonKhoBanDau;
            tonKhoBanDau = null;
            datTonKho(original);
            tonKhoBanDau = null;
        }
    }

    private NguoiDung user(String tenDangNhap) {
        return nguoiDungRepository.findByTenDangNhap(tenDangNhap);
    }

    private void datTonKho(int giaTri) {
        if (tonKhoBanDau == null) {
            tonKhoBanDau = tonKho();
        }
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
        int ownerId = owner.getMaNguoiDung();
        Long maDonHang = new TransactionTemplate(txManager).execute(status -> {
            DonHang don = new DonHang();
            don.setNgayTao(new Date());
            don.setNguoiDung(nguoiDungRepository.findById((long) ownerId).orElseThrow());
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
        if (maDonHang == null) {
            throw new IllegalStateException("Không thể tạo đơn hàng test");
        }
        donHangFixtures.add(maDonHang);
        return maDonHang;
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
        CountDownLatch daTaiStaleOrder = new CountDownLatch(2);

        List<Integer> results = chayDongThoi(
                () -> huyTrongTransactionSauHangRao(id, daTaiStaleOrder),
                () -> huyTrongTransactionSauHangRao(id, daTaiStaleOrder));

        assertThat(results).containsExactlyInAnyOrder(1, 409);
        assertThat(tonKho()).as("kho chi hoan 1 lan (5+2)").isEqualTo(7);
    }

    private int huyTrongTransactionSauHangRao(
            Long maDonHang,
            CountDownLatch daTaiStaleOrder) {
        try {
            Integer result = new TransactionTemplate(txManager).execute(status -> {
                DonHang staleOrder = donHangRepository.findById(maDonHang).orElseThrow();
                assertThat(staleOrder.getTrangThaiGiaoHang())
                        .isEqualTo(TrangThaiGiaoHang.CHO_XU_LY.getGiaTri());
                daTaiStaleOrder.countDown();
                choTinHieu(daTaiStaleOrder, "Workers did not load the same stale order in time");
                donHangHuyService.huyDon(maDonHang, user("user1"), false);
                return 1;
            });
            return result == null ? 0 : result;
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == 409) {
                return 409;
            }
            throw e;
        }
    }

    private List<Integer> chayDongThoi(Worker dauTien, Worker thuHai) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch batDau = new CountDownLatch(1);
        try {
            Future<Integer> first = pool.submit(() -> {
                choTinHieu(batDau, "Workers did not receive the start signal in time");
                return dauTien.chay();
            });
            Future<Integer> second = pool.submit(() -> {
                choTinHieu(batDau, "Workers did not receive the start signal in time");
                return thuHai.chay();
            });
            batDau.countDown();
            return List.of(
                    first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void choTinHieu(CountDownLatch latch, String message) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message, e);
        }
    }

    @FunctionalInterface
    private interface Worker {
        int chay() throws Exception;
    }
}
