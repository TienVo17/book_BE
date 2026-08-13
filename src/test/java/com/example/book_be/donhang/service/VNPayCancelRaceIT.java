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
import com.example.book_be.thanhtoan.config.VnPayConfig;
import com.example.book_be.thanhtoan.domain.HinhThucThanhToan;
import com.example.book_be.thanhtoan.repository.HinhThucThanhToanRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bao ve hai tinh huong khac nhau cua VNPay va huy don:
 * - Khi callback va huy dua dong thoi, khoa hang dung chung tuan tu hoa ca hai transaction;
 *   ket qua cuoi cung phai la DA_HUY + DA_THANH_TOAN.
 * - Khi gateway xac nhan thanh toan sau khi huy da commit (URL cu khong the thu hoi), payment
 *   phai duoc ghi nhan nhung delivery van DA_HUY, khong giao lai va khong doi kho/coupon lan nua.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = false)
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
    @Autowired HinhThucThanhToanRepository hinhThucThanhToanRepository;
    @Autowired LichSuTrangThaiDonHangRepository lichSuRepository;
    @Autowired PlatformTransactionManager txManager;
    @Autowired MockMvc mvc;
    @Autowired VnPayConfig vnPayConfig;

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
            HinhThucThanhToan vnpay = hinhThucThanhToanRepository.findByMaCodeIgnoreCase("VNPAY")
                    .orElseThrow();
            don.setHinhThucThanhToan(vnpay);
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
    void vnpay_thanh_cong_dua_voi_admin_huy_ca_hai_hoan_tat_tuan_tu() throws Exception {
        datTonKho(5);
        taoCoupon();
        Long id = taoDonChoXuLy(user("user1"), 2);
        CountDownLatch daTaiStaleOrder = new CountDownLatch(2);

        List<Integer> results = chayDongThoi(
                () -> thanhToanTrongTransactionSauHangRao(id, daTaiStaleOrder),
                () -> huyTrongTransactionSauHangRao(id, daTaiStaleOrder));

        assertThat(results).containsExactlyInAnyOrder(PAYMENT_SUCCESS, CANCEL_SUCCESS);

        DonHang cuoiCung = donHangRepository.findById(id).orElseThrow();
        assertThat(cuoiCung.getTrangThaiGiaoHang()).isEqualTo(TrangThaiGiaoHang.DA_HUY.getGiaTri());
        assertThat(cuoiCung.getTrangThaiThanhToan()).isEqualTo(TrangThaiThanhToan.DA_THANH_TOAN.getGiaTri());
        assertThat(tonKho()).as("huy don hoan kho dung mot lan").isEqualTo(5);
        assertThat(daSuDungCoupon()).as("huy don hoan coupon dung mot lan").isEqualTo(0);
    }

    @Test
    void vnpay_xac_nhan_sau_khi_huy_ghi_nhan_tien_nhung_khong_hoi_sinh_don() {
        datTonKho(5);
        taoCoupon();
        Long id = taoDonChoXuLy(user("user1"), 2);

        new TransactionTemplate(txManager).executeWithoutResult(status ->
                donHangHuyService.huyDon(id, user("admin"), true));

        assertThat(tonKho()).as("huy don da hoan kho").isEqualTo(5);
        assertThat(daSuDungCoupon()).as("huy don da hoan coupon").isEqualTo(0);

        assertThat(donHangRepository.findById(id).orElseThrow().getTrangThaiGiaoHang())
                .isEqualTo(TrangThaiGiaoHang.DA_HUY.getGiaTri());
        assertThat(goiCallbackThanhCong(id, 10000000L)).isEqualTo("ordercancelledpaid");

        DonHang sauCallback = donHangRepository.findById(id).orElseThrow();
        assertThat(sauCallback.getTrangThaiGiaoHang()).isEqualTo(TrangThaiGiaoHang.DA_HUY.getGiaTri());
        assertThat(sauCallback.getTrangThaiThanhToan()).isEqualTo(TrangThaiThanhToan.DA_THANH_TOAN.getGiaTri());

        assertThat(goiCallbackThanhCong(id, 10000000L)).isEqualTo("ordercancelledpaid");

        assertThat(tonKho()).as("late payment khong tru kho lan nua").isEqualTo(5);
        assertThat(daSuDungCoupon()).as("late payment khong dung lai coupon").isEqualTo(0);
    }

    private String goiCallbackThanhCong(Long maDonHang, long soTienVnPay) {
        Map<String, String> fields = new HashMap<>();
        fields.put("vnp_Amount", String.valueOf(soTienVnPay));
        fields.put("vnp_OrderInfo", String.valueOf(maDonHang));
        fields.put("vnp_ResponseCode", "00");
        fields.put("vnp_TransactionStatus", "00");

        Map<String, String> encodedFields = new HashMap<>();
        fields.forEach((name, value) -> encodedFields.put(encode(name), encode(value)));
        String signature = vnPayConfig.hashAllFields(encodedFields);
        try {
            return mvc.perform(get("/api/don-hang/vnpay-payment")
                            .params(toParams(fields))
                            .param("vnp_SecureHash", signature))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException("Khong goi duoc VNPay callback test", e);
        }
    }

    private org.springframework.util.MultiValueMap<String, String> toParams(Map<String, String> fields) {
        org.springframework.util.LinkedMultiValueMap<String, String> params =
                new org.springframework.util.LinkedMultiValueMap<>();
        fields.forEach(params::add);
        return params;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    /** Mo phong callback VNPay thanh cong bang dung transaction service cua production. */
    private int thanhToanTrongTransactionSauHangRao(Long maDonHang, CountDownLatch daTaiStaleOrder) {
        DonHang staleOrder = donHangRepository.findById(maDonHang).orElseThrow();
        assertThat(staleOrder.getTrangThaiGiaoHang()).isEqualTo(TrangThaiGiaoHang.CHO_XU_LY.getGiaTri());
        daTaiStaleOrder.countDown();
        choTinHieu(daTaiStaleOrder, "Workers did not load the same stale order in time");
        assertThat(donHangTrangThaiService.xuLyThanhToanVnPayThanhCong(maDonHang, 10000000L))
                .isEqualTo("ordersuccess");
        return PAYMENT_SUCCESS;
    }

    /** Mo phong admin huy don dang cho xu ly. */
    private int huyTrongTransactionSauHangRao(Long maDonHang, CountDownLatch daTaiStaleOrder) {
        DonHang staleOrder = donHangRepository.findById(maDonHang).orElseThrow();
        assertThat(staleOrder.getTrangThaiGiaoHang()).isEqualTo(TrangThaiGiaoHang.CHO_XU_LY.getGiaTri());
        daTaiStaleOrder.countDown();
        choTinHieu(daTaiStaleOrder, "Workers did not load the same stale order in time");
        donHangHuyService.huyDon(maDonHang, user("admin"), true);
        return CANCEL_SUCCESS;
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
