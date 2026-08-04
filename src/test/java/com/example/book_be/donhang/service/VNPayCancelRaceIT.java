package com.example.book_be.donhang.service;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.donhang.domain.ChiTietDonHang;
import com.example.book_be.donhang.domain.DonHang;
import com.example.book_be.donhang.domain.TrangThaiGiaoHang;
import com.example.book_be.donhang.domain.TrangThaiThanhToan;
import com.example.book_be.donhang.repository.ChiTietDonHangRepository;
import com.example.book_be.donhang.repository.DonHangRepository;
import com.example.book_be.donhang.repository.LichSuTrangThaiDonHangRepository;
import com.example.book_be.giamgia.domain.Coupon;
import com.example.book_be.giamgia.domain.LoaiGiamGia;
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

/**
 * VNPay callback (thanh toan thanh cong) dua voi admin huy tren CUNG 1 don hang. Chung minh
 * @Version optimistic lock hien co da du de bao dam dung 1 phia thanh cong, khong bao gio ket
 * thuc o trang thai "da huy + hoan kho/coupon" VA "da thanh toan" cung luc.
 *
 * Neu test nay xanh voi co che @Version hien co, KHONG duoc sua production code (theo yeu cau
 * phase). Chi khi test chung minh vi pham bat bien moi duoc them 1 lop khoa/giao dich.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfig.class)
class VNPayCancelRaceIT {

    private static final int MA_SACH = 1;
    private static final String MA_COUPON = "VNPAYRACE";
    private static final long TIMEOUT_SECONDS = 20;
    private static final int PAYMENT_SUCCESS = 1;
    private static final int CANCEL_SUCCESS = 2;

    @Autowired DonHangHuyService donHangHuyService;
    @Autowired DonHangTrangThaiService donHangTrangThaiService;
    @Autowired DonHangRepository donHangRepository;
    @Autowired ChiTietDonHangRepository chiTietDonHangRepository;
    @Autowired SachRepository sachRepository;
    @Autowired CouponRepository couponRepository;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired LichSuTrangThaiDonHangRepository lichSuRepository;
    @Autowired PlatformTransactionManager txManager;

    private final List<Long> donHangFixtures = new ArrayList<>();
    private Integer tonKhoBanDau;
    private Integer maCouponFixture;

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
            if (maCouponFixture != null) {
                couponRepository.deleteById((long) maCouponFixture);
            }
        });
        if (tonKhoBanDau != null) {
            int original = tonKhoBanDau;
            tonKhoBanDau = null;
            new TransactionTemplate(txManager).executeWithoutResult(status -> {
                Sach sach = sachRepository.findById((long) MA_SACH).orElseThrow();
                sach.setSoLuong(original);
                sachRepository.saveAndFlush(sach);
            });
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

    private int daSuDungCoupon() {
        return couponRepository.findById((long) maCouponFixture).orElseThrow().getDaSuDung();
    }

    /** Coupon FIXED voi da_su_dung=1, giong trang thai ngay sau khi checkout that su dung coupon. */
    private void taoCoupon() {
        Coupon saved = new TransactionTemplate(txManager).execute(status -> {
            Coupon coupon = new Coupon();
            coupon.setMa(MA_COUPON);
            coupon.setLoai(LoaiGiamGia.FIXED);
            coupon.setGiaTriGiam(1000);
            coupon.setGiaTriToiThieu(0);
            coupon.setSoLuongToiDa(100);
            coupon.setDaSuDung(1);
            coupon.setIsActive(true);
            return couponRepository.saveAndFlush(coupon);
        });
        assertThat(saved).isNotNull();
        maCouponFixture = saved.getMaCoupon();
    }

    /**
     * Don CHO_XU_LY / CHUA_THANH_TOAN, gan coupon, 1 chi tiet tren MA_SACH. Tru kho ngay luc tao
     * (giong hanh vi checkout that: kho da bi giu cho don nay), de test phan anh dung bat bien
     * "huy hoan lai kho da giu / thanh toan giu nguyen kho da giu".
     */
    private Long taoDonChoXuLy(NguoiDung owner, int soLuong) {
        int ownerId = owner.getMaNguoiDung();
        Long maDonHang = new TransactionTemplate(txManager).execute(status -> {
            DonHang don = new DonHang();
            don.setNgayTao(new Date());
            don.setNguoiDung(nguoiDungRepository.findById((long) ownerId).orElseThrow());
            don.setHoTen("IT VNPay Race");
            don.setSoDienThoai("0900000000");
            don.setTongTien(100000);
            don.setTrangThaiThanhToan(TrangThaiThanhToan.CHUA_THANH_TOAN.getGiaTri());
            don.setTrangThaiGiaoHang(TrangThaiGiaoHang.CHO_XU_LY.getGiaTri());
            don.setMaCoupon(maCouponFixture);
            donHangRepository.saveAndFlush(don);

            Sach sach = sachRepository.findById((long) MA_SACH).orElseThrow();
            if (sachRepository.truKhoNeuDu(MA_SACH, soLuong) == 0) {
                throw new IllegalStateException("Fixture setup: khong du ton kho de giu cho don test");
            }
            ChiTietDonHang ct = new ChiTietDonHang();
            ct.setDonHang(don);
            ct.setSach(sach);
            ct.setSoLuong(soLuong);
            ct.setGiaBan(sach.getGiaBan());
            chiTietDonHangRepository.saveAndFlush(ct);
            return (long) don.getMaDonHang();
        });
        if (maDonHang == null) {
            throw new IllegalStateException("Khong the tao don hang test");
        }
        donHangFixtures.add(maDonHang);
        return maDonHang;
    }

    @Test
    void vnpay_thanh_cong_dua_voi_admin_huy_chi_1_ben_thanh_cong_khong_bao_gio_vua_huy_vua_thanh_toan() throws Exception {
        datTonKho(5);
        taoCoupon();
        Long id = taoDonChoXuLy(user("user1"), 2);
        CountDownLatch daTaiStaleOrder = new CountDownLatch(2);

        List<Integer> results = chayDongThoi(
                () -> thanhToanTrongTransactionSauHangRao(id, daTaiStaleOrder),
                () -> huyTrongTransactionSauHangRao(id, daTaiStaleOrder));

        boolean thanhToanThang = results.contains(PAYMENT_SUCCESS);
        boolean huyThang = results.contains(CANCEL_SUCCESS);
        assertThat(thanhToanThang ^ huyThang).as("dung 1 phia thanh cong: " + results).isTrue();
        assertThat(results).as("phia con lai bi optimistic lock 409").contains(409);

        DonHang cuoiCung = donHangRepository.findById(id).orElseThrow();

        if (thanhToanThang) {
            assertThat(cuoiCung.getTrangThaiGiaoHang()).isEqualTo(TrangThaiGiaoHang.DANG_GIAO.getGiaTri());
            assertThat(cuoiCung.getTrangThaiThanhToan()).isEqualTo(TrangThaiThanhToan.DA_THANH_TOAN.getGiaTri());
            assertThat(tonKho()).as("kho van bi tru, huy khong hoan lai").isEqualTo(3);
            assertThat(daSuDungCoupon()).as("coupon van o trang thai da dung").isEqualTo(1);
        } else {
            assertThat(cuoiCung.getTrangThaiGiaoHang()).isEqualTo(TrangThaiGiaoHang.DA_HUY.getGiaTri());
            assertThat(cuoiCung.getTrangThaiThanhToan()).isNotEqualTo(TrangThaiThanhToan.DA_THANH_TOAN.getGiaTri());
            assertThat(tonKho()).as("kho duoc hoan lai boi huy don").isEqualTo(5);
            assertThat(daSuDungCoupon()).as("coupon duoc hoan luot boi huy don").isEqualTo(0);
        }

        // Bat bien tuyet doi (ACCEPTED INVARIANT): khong duoc vua huy+hoan kho vua da thanh toan.
        boolean daHuyVaHoanKho = cuoiCung.getTrangThaiGiaoHang() == TrangThaiGiaoHang.DA_HUY.getGiaTri();
        boolean daThanhToan = TrangThaiThanhToan.from(cuoiCung.getTrangThaiThanhToan()) == TrangThaiThanhToan.DA_THANH_TOAN;
        assertThat(daHuyVaHoanKho && daThanhToan)
                .as("khong duoc vua huy(hoan kho/coupon) vua thanh toan thanh cong")
                .isFalse();
    }

    /** Mo phong VNPay callback thanh cong: chuyen giao hang DANG_GIAO roi chuyen thanh toan DA_THANH_TOAN. */
    private int thanhToanTrongTransactionSauHangRao(Long maDonHang, CountDownLatch daTaiStaleOrder) {
        try {
            Integer result = new TransactionTemplate(txManager).execute(status -> {
                DonHang staleOrder = donHangRepository.findById(maDonHang).orElseThrow();
                assertThat(staleOrder.getTrangThaiGiaoHang()).isEqualTo(TrangThaiGiaoHang.CHO_XU_LY.getGiaTri());
                daTaiStaleOrder.countDown();
                choTinHieu(daTaiStaleOrder, "Workers did not load the same stale order in time");
                donHangTrangThaiService.chuyenTrangThaiGiaoHang(staleOrder, TrangThaiGiaoHang.DANG_GIAO, "VNPAY");
                donHangTrangThaiService.chuyenTrangThaiThanhToan(staleOrder, TrangThaiThanhToan.DA_THANH_TOAN, "VNPAY");
                return PAYMENT_SUCCESS;
            });
            return result == null ? 0 : result;
        } catch (ResponseStatusException e) {
            return e.getStatusCode().value();
        }
    }

    /** Mo phong admin huy don dang cho xu ly. */
    private int huyTrongTransactionSauHangRao(Long maDonHang, CountDownLatch daTaiStaleOrder) {
        try {
            Integer result = new TransactionTemplate(txManager).execute(status -> {
                DonHang staleOrder = donHangRepository.findById(maDonHang).orElseThrow();
                assertThat(staleOrder.getTrangThaiGiaoHang()).isEqualTo(TrangThaiGiaoHang.CHO_XU_LY.getGiaTri());
                daTaiStaleOrder.countDown();
                choTinHieu(daTaiStaleOrder, "Workers did not load the same stale order in time");
                donHangHuyService.huyDon(maDonHang, user("admin"), true);
                return CANCEL_SUCCESS;
            });
            return result == null ? 0 : result;
        } catch (ResponseStatusException e) {
            return e.getStatusCode().value();
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
