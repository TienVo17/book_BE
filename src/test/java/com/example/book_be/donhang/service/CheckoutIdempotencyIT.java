package com.example.book_be.donhang.service;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.donhang.domain.ChiTietDonHang;
import com.example.book_be.donhang.domain.DonHang;
import com.example.book_be.donhang.dto.CheckoutOrderRequest;
import com.example.book_be.donhang.dto.CheckoutOrderResponse;
import com.example.book_be.donhang.repository.ChiTietDonHangRepository;
import com.example.book_be.donhang.repository.DonHangRepository;
import com.example.book_be.donhang.web.DonHangController;
import com.example.book_be.giamgia.domain.Coupon;
import com.example.book_be.giamgia.domain.LoaiGiamGia;
import com.example.book_be.giamgia.repository.CouponRepository;
import com.example.book_be.giohang.dto.CartItemRequest;
import com.example.book_be.nguoidung.domain.DiaChiGiaoHang;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.domain.Quyen;
import com.example.book_be.nguoidung.repository.DiaChiGiaoHangRepository;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.repository.QuyenRepository;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.sach.repository.SachRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checkout idempotency tren MySQL Testcontainers that: cung user + cung Idempotency-Key phai
 * claim/replay 1 don hang duy nhat (tuan tu lan dong thoi), cung key + request khac ban chat
 * phai 409 khong mutation, key khac nhau doc lap, va mot checkout that bai KHONG duoc giu key
 * vinh vien (retry sau do voi request hop le phai thanh cong).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfig.class)
class CheckoutIdempotencyIT {

    private static final long TIMEOUT_SECONDS = 20;
    private static final String PAYMENT_METHOD_COD = "COD";

    @Autowired DonHangController donHangController;
    @Autowired DonHangRepository donHangRepository;
    @Autowired ChiTietDonHangRepository chiTietDonHangRepository;
    @Autowired SachRepository sachRepository;
    @Autowired CouponRepository couponRepository;
    @Autowired DiaChiGiaoHangRepository diaChiGiaoHangRepository;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired QuyenRepository quyenRepository;
    @Autowired PlatformTransactionManager txManager;

    private final Set<Long> donHangFixtures = new LinkedHashSet<>();
    private final List<Integer> sachFixtures = new ArrayList<>();
    private final List<Long> diaChiFixtures = new ArrayList<>();
    private final List<Long> couponFixtures = new ArrayList<>();
    private final List<Long> userFixtures = new ArrayList<>();
    private String fixtureRunId;
    private NguoiDung owner;

    @BeforeEach
    void provisionOwner() {
        fixtureRunId = "checkout-idem-" + System.nanoTime();
        owner = taoNguoiDung("owner");
    }

    @AfterEach
    void cleanupFixtures() {
        SecurityContextHolder.clearContext();
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            for (Long maDonHang : donHangFixtures) {
                List<ChiTietDonHang> chiTiets = chiTietDonHangRepository.findAll().stream()
                        .filter(item -> item.getDonHang() != null
                                && item.getDonHang().getMaDonHang() == maDonHang.intValue())
                        .toList();
                chiTietDonHangRepository.deleteAll(chiTiets);
                if (donHangRepository.existsById(maDonHang)) {
                    donHangRepository.deleteById(maDonHang);
                }
            }
            for (Long maDiaChi : diaChiFixtures) {
                if (diaChiGiaoHangRepository.existsById(maDiaChi)) {
                    diaChiGiaoHangRepository.deleteById(maDiaChi);
                }
            }
            for (Integer maSach : sachFixtures) {
                if (sachRepository.existsById(maSach.longValue())) {
                    sachRepository.deleteById(maSach.longValue());
                }
            }
            for (Long maCoupon : couponFixtures) {
                if (couponRepository.existsById(maCoupon)) {
                    couponRepository.deleteById(maCoupon);
                }
            }
            for (Long maNguoiDung : userFixtures) {
                if (nguoiDungRepository.existsById(maNguoiDung)) {
                    nguoiDungRepository.deleteById(maNguoiDung);
                }
            }
        });
    }

    @Test
    void tuan_tu_lap_lai_cung_key_tra_ve_cung_don_va_chi_1_lan_mutation() {
        Sach sach = taoSach(20);
        DiaChiGiaoHang diaChi = taoDiaChi(owner);
        Coupon coupon = taoCoupon("SEQ" + System.nanoTime() % 100000, 100);
        CheckoutOrderRequest request = checkoutRequest(sach.getMaSach(), 2, diaChi.getMaDiaChi(), coupon.getMa());
        String key = "seq-key-" + System.nanoTime();

        ResponseEntity<?> first = checkout(owner, request, key);
        ResponseEntity<?> second = checkout(owner, request, key);

        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        CheckoutOrderResponse firstBody = (CheckoutOrderResponse) first.getBody();
        CheckoutOrderResponse secondBody = (CheckoutOrderResponse) second.getBody();
        assertThat(firstBody).isNotNull();
        assertThat(secondBody).isEqualTo(firstBody);
        donHangFixtures.add(firstBody.getMaDonHang().longValue());

        assertThat(tonKho(sach.getMaSach())).as("kho chi tru dung 1 lan").isEqualTo(18);
        assertThat(daSuDungCoupon(coupon.getMaCoupon())).as("coupon chi tang dung 1 lan").isEqualTo(1);
        DonHang persisted = donHangRepository
                .findByNguoiDung_MaNguoiDungAndCheckoutIdempotencyKey(owner.getMaNguoiDung(), key)
                .orElseThrow();
        assertThat(persisted.getMaDonHang()).as("dung 1 don gan voi key nay").isEqualTo(firstBody.getMaDonHang());
    }

    @Test
    void replay_giu_nguyen_response_da_commit_khi_coupon_bi_doi_ma_hoac_xoa() {
        Sach sach = taoSach(20);
        DiaChiGiaoHang diaChi = taoDiaChi(owner);
        Coupon coupon = taoCoupon("SNAP" + System.nanoTime() % 100000, 100);
        CheckoutOrderRequest request = checkoutRequest(sach.getMaSach(), 2, diaChi.getMaDiaChi(), coupon.getMa());
        String key = "snapshot-key-" + System.nanoTime();

        ResponseEntity<?> first = checkout(owner, request, key);
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        CheckoutOrderResponse firstBody = (CheckoutOrderResponse) first.getBody();
        assertThat(firstBody).isNotNull();
        donHangFixtures.add(firstBody.getMaDonHang().longValue());

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Coupon mutableCoupon = couponRepository.findById((long) coupon.getMaCoupon()).orElseThrow();
            mutableCoupon.setMa("CHANGED" + System.nanoTime() % 100000);
            couponRepository.saveAndFlush(mutableCoupon);
        });

        ResponseEntity<?> replayAfterRename = checkout(owner, request, key);
        assertThat(replayAfterRename.getStatusCode().value()).isEqualTo(200);
        assertThat(replayAfterRename.getBody()).isEqualTo(firstBody);

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            couponRepository.deleteById((long) coupon.getMaCoupon());
            couponRepository.flush();
        });
        couponFixtures.remove((long) coupon.getMaCoupon());

        ResponseEntity<?> replayAfterDelete = checkout(owner, request, key);
        assertThat(replayAfterDelete.getStatusCode().value()).isEqualTo(200);
        assertThat(replayAfterDelete.getBody()).isEqualTo(firstBody);
        assertThat(tonKho(sach.getMaSach())).isEqualTo(18);
    }

    @Test
    void replay_giu_nguyen_trang_thai_thanh_toan_da_tra_du_trang_thai_don_sau_do_thay_doi() {
        Sach sach = taoSach(20);
        DiaChiGiaoHang diaChi = taoDiaChi(owner);
        CheckoutOrderRequest request = checkoutRequest(sach.getMaSach(), 1, diaChi.getMaDiaChi(), null);
        String key = "payment-snapshot-key-" + System.nanoTime();

        ResponseEntity<?> first = checkout(owner, request, key);
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        CheckoutOrderResponse firstBody = (CheckoutOrderResponse) first.getBody();
        assertThat(firstBody).isNotNull();
        assertThat(firstBody.getTrangThaiThanhToan()).isZero();
        donHangFixtures.add(firstBody.getMaDonHang().longValue());

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            DonHang mutableOrder = donHangRepository.findById(firstBody.getMaDonHang().longValue()).orElseThrow();
            mutableOrder.setTrangThaiThanhToan(1);
            donHangRepository.saveAndFlush(mutableOrder);
        });

        ResponseEntity<?> replay = checkout(owner, request, key);
        assertThat(replay.getStatusCode().value()).isEqualTo(200);
        assertThat(replay.getBody()).isEqualTo(firstBody);
    }

    @Test
    void hai_thread_dong_thoi_cung_key_chi_tao_1_don_1_tru_kho_1_tang_coupon() throws Exception {
        Sach sach = taoSach(20);
        DiaChiGiaoHang diaChi = taoDiaChi(owner);
        Coupon coupon = taoCoupon("RACE" + System.nanoTime() % 100000, 100);
        CheckoutOrderRequest request = checkoutRequest(sach.getMaSach(), 2, diaChi.getMaDiaChi(), coupon.getMa());
        String key = "race-key-" + System.nanoTime();

        List<ResponseEntity<?>> results = chayDongThoi(
                () -> checkout(owner, request, key),
                () -> checkout(owner, request, key));

        assertThat(results).hasSize(2);
        for (ResponseEntity<?> response : results) {
            assertThat(response.getStatusCode().value()).as("ca 2 phia deu thanh cong (thang tao, thua replay)").isEqualTo(200);
        }
        CheckoutOrderResponse bodyA = (CheckoutOrderResponse) results.get(0).getBody();
        CheckoutOrderResponse bodyB = (CheckoutOrderResponse) results.get(1).getBody();
        assertThat(bodyA).isNotNull();
        assertThat(bodyB).isNotNull();
        assertThat(bodyA.getMaDonHang()).as("ca 2 phia tra ve cung 1 don").isEqualTo(bodyB.getMaDonHang());
        donHangFixtures.add(bodyA.getMaDonHang().longValue());

        assertThat(tonKho(sach.getMaSach())).as("kho chi tru dung 1 lan du 2 request dong thoi").isEqualTo(18);
        assertThat(daSuDungCoupon(coupon.getMaCoupon())).as("coupon chi tang dung 1 lan du 2 request dong thoi").isEqualTo(1);
    }

    @Test
    void cung_key_request_khac_ban_chat_tra_409_khong_mutation() {
        Sach sach = taoSach(20);
        DiaChiGiaoHang diaChi = taoDiaChi(owner);
        String key = "conflict-key-" + System.nanoTime();
        CheckoutOrderRequest first = checkoutRequest(sach.getMaSach(), 2, diaChi.getMaDiaChi(), null);
        CheckoutOrderRequest different = checkoutRequest(sach.getMaSach(), 5, diaChi.getMaDiaChi(), null);

        ResponseEntity<?> firstResponse = checkout(owner, first, key);
        assertThat(firstResponse.getStatusCode().value()).isEqualTo(200);
        CheckoutOrderResponse firstBody = (CheckoutOrderResponse) firstResponse.getBody();
        assertThat(firstBody).isNotNull();
        donHangFixtures.add(firstBody.getMaDonHang().longValue());
        int tonKhoSauLan1 = tonKho(sach.getMaSach());

        ResponseEntity<?> conflictResponse = checkout(owner, different, key);

        assertThat(conflictResponse.getStatusCode().value()).isEqualTo(409);
        assertThat(tonKho(sach.getMaSach())).as("khong tru them kho cho request bi tu choi").isEqualTo(tonKhoSauLan1);
        DonHang persisted = donHangRepository
                .findByNguoiDung_MaNguoiDungAndCheckoutIdempotencyKey(owner.getMaNguoiDung(), key)
                .orElseThrow();
        assertThat(persisted.getMaDonHang()).isEqualTo(firstBody.getMaDonHang());
    }

    @Test
    void key_khac_nhau_tao_don_doc_lap() {
        Sach sach = taoSach(20);
        DiaChiGiaoHang diaChi = taoDiaChi(owner);
        CheckoutOrderRequest requestA = checkoutRequest(sach.getMaSach(), 1, diaChi.getMaDiaChi(), null);
        CheckoutOrderRequest requestB = checkoutRequest(sach.getMaSach(), 1, diaChi.getMaDiaChi(), null);

        ResponseEntity<?> responseA = checkout(owner, requestA, "key-A-" + System.nanoTime());
        ResponseEntity<?> responseB = checkout(owner, requestB, "key-B-" + System.nanoTime());

        assertThat(responseA.getStatusCode().value()).isEqualTo(200);
        assertThat(responseB.getStatusCode().value()).isEqualTo(200);
        CheckoutOrderResponse bodyA = (CheckoutOrderResponse) responseA.getBody();
        CheckoutOrderResponse bodyB = (CheckoutOrderResponse) responseB.getBody();
        assertThat(bodyA).isNotNull();
        assertThat(bodyB).isNotNull();
        assertThat(bodyA.getMaDonHang()).isNotEqualTo(bodyB.getMaDonHang());
        donHangFixtures.add(bodyA.getMaDonHang().longValue());
        donHangFixtures.add(bodyB.getMaDonHang().longValue());
        assertThat(tonKho(sach.getMaSach())).isEqualTo(18);
    }

    @Test
    void checkout_that_bai_khong_giu_key_vinh_vien_retry_sau_do_thanh_cong() {
        Sach sach = taoSach(1);
        DiaChiGiaoHang diaChi = taoDiaChi(owner);
        String key = "retry-after-fail-" + System.nanoTime();
        CheckoutOrderRequest khongDuKho = checkoutRequest(sach.getMaSach(), 5, diaChi.getMaDiaChi(), null);

        ResponseEntity<?> failed = checkout(owner, khongDuKho, key);
        assertThat(failed.getStatusCode().value()).isEqualTo(400);
        assertThat(tonKho(sach.getMaSach())).as("khong tru kho khi that bai").isEqualTo(1);
        assertThat(donHangRepository.findByNguoiDung_MaNguoiDungAndCheckoutIdempotencyKey(owner.getMaNguoiDung(), key))
                .as("key khong bi giu lai boi transaction da rollback").isEmpty();

        CheckoutOrderRequest hopLe = checkoutRequest(sach.getMaSach(), 1, diaChi.getMaDiaChi(), null);
        ResponseEntity<?> retried = checkout(owner, hopLe, key);

        assertThat(retried.getStatusCode().value()).as("retry cung key sau that bai phai thanh cong").isEqualTo(200);
        CheckoutOrderResponse body = (CheckoutOrderResponse) retried.getBody();
        assertThat(body).isNotNull();
        donHangFixtures.add(body.getMaDonHang().longValue());
        assertThat(tonKho(sach.getMaSach())).isEqualTo(0);
    }

    /**
     * Khong co key thi mot request gui lai se tao don trung va tru kho lan nua — thiet hai that,
     * client khong the tu sua. Server phai tu choi truoc khi cham vao kho.
     */
    @Test
    void thieu_hoac_rong_idempotency_key_bi_tu_choi_va_khong_tao_don() {
        Sach sach = taoSach(20);
        DiaChiGiaoHang diaChi = taoDiaChi(owner);
        CheckoutOrderRequest request = checkoutRequest(sach.getMaSach(), 1, diaChi.getMaDiaChi(), null);

        ResponseEntity<?> missingHeader = checkout(owner, request, null);
        ResponseEntity<?> blankHeader = checkout(owner, request, "   ");

        assertThat(missingHeader.getStatusCode().value()).isEqualTo(400);
        assertThat(blankHeader.getStatusCode().value()).isEqualTo(400);
        assertThat(tonKho(sach.getMaSach()))
                .as("request bi tu choi khong duoc tru kho")
                .isEqualTo(20);
    }

    @Test
    void key_qua_dai_hoac_ky_tu_khong_hop_le_tra_400_khong_mutation() {
        Sach sach = taoSach(20);
        DiaChiGiaoHang diaChi = taoDiaChi(owner);
        CheckoutOrderRequest request = checkoutRequest(sach.getMaSach(), 1, diaChi.getMaDiaChi(), null);

        ResponseEntity<?> tooLong = checkout(owner, request, "a".repeat(101));
        ResponseEntity<?> invalidChars = checkout(owner, request, "khong hop le !@#");

        assertThat(tooLong.getStatusCode().value()).isEqualTo(400);
        assertThat(invalidChars.getStatusCode().value()).isEqualTo(400);
        assertThat(tonKho(sach.getMaSach())).as("key khong hop le khong duoc gay mutation nao").isEqualTo(20);
    }

    private ResponseEntity<?> checkout(NguoiDung user, CheckoutOrderRequest request, String idempotencyKeyHeader) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getTenDangNhap(), null, List.of()));
        try {
            return donHangController.add(request, idempotencyKeyHeader);
        } catch (ResponseStatusException exception) {
            return ResponseEntity.status(exception.getStatusCode()).body(exception.getReason());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private CheckoutOrderRequest checkoutRequest(int maSach, int soLuong, int maDiaChi, String maCoupon) {
        return new CheckoutOrderRequest(
                new ArrayList<>(List.of(new CartItemRequest(maSach, soLuong))),
                maDiaChi, PAYMENT_METHOD_COD, maCoupon);
    }

    private int tonKho(int maSach) {
        return sachRepository.findSoLuongByMaSach(maSach);
    }

    private int daSuDungCoupon(int maCoupon) {
        return couponRepository.findById((long) maCoupon).orElseThrow().getDaSuDung();
    }

    /**
     * NguoiDung.danhSachQuyen cascades PERSIST, so a Quyen loaded outside this transaction would
     * arrive detached and fail the cascade. Load the role and save the user inside one
     * transaction so the role stays managed.
     */
    private NguoiDung taoNguoiDung(String suffix) {
        NguoiDung saved = new TransactionTemplate(txManager).execute(status -> {
            Quyen userRole = quyenRepository.findByTenQuyen("USER");
            assertThat(userRole).as("seed USER role exists").isNotNull();

            NguoiDung user = new NguoiDung();
            user.setHoDem("Checkout");
            user.setTen("Idempotency");
            user.setTenDangNhap(fixtureRunId + "-" + suffix);
            user.setMatKhau("unused-in-this-it");
            user.setGioiTinh('X');
            user.setEmail(fixtureRunId + "-" + suffix + "@example.test");
            user.setSoDienThoai("0900000000");
            user.setDiaChiMuaHang("Checkout idempotency fixture address");
            user.setDiaChiGiaoHang("Checkout idempotency fixture delivery address");
            user.setDaKichHoat(true);
            user.setDanhSachQuyen(List.of(userRole));

            return nguoiDungRepository.saveAndFlush(user);
        });

        assertThat(saved).isNotNull();
        userFixtures.add((long) saved.getMaNguoiDung());
        return saved;
    }

    private Sach taoSach(int soLuong) {
        Sach saved = new TransactionTemplate(txManager).execute(status -> {
            Sach sach = new Sach();
            sach.setTenSach("Checkout idempotency fixture " + System.nanoTime());
            sach.setTenTacGia("IT Fixture");
            sach.setGiaBan(10000D);
            sach.setGiaNiemYet(12000D);
            sach.setSoLuong(soLuong);
            sach.setIsActive(1);
            return sachRepository.saveAndFlush(sach);
        });
        assertThat(saved).isNotNull();
        sachFixtures.add(saved.getMaSach());
        return saved;
    }

    /**
     * DiaChiGiaoHang.nguoiDung cascades PERSIST, so the owner must be re-read inside the same
     * transaction that saves the address; passing the detached fixture instance would fail.
     */
    private DiaChiGiaoHang taoDiaChi(NguoiDung owner) {
        int ownerId = owner.getMaNguoiDung();
        DiaChiGiaoHang saved = new TransactionTemplate(txManager).execute(status -> {
            NguoiDung managedOwner = nguoiDungRepository.findById((long) ownerId)
                    .orElseThrow(() -> new IllegalStateException("fixture user missing"));

            DiaChiGiaoHang address = new DiaChiGiaoHang();
            address.setNguoiDung(managedOwner);
            address.setHoTen("Checkout idempotency fixture");
            address.setSoDienThoai("0900000000");
            address.setDiaChiDayDu("Checkout idempotency fixture address line");
            address.setMacDinh(false);
            return diaChiGiaoHangRepository.saveAndFlush(address);
        });
        assertThat(saved).isNotNull();
        diaChiFixtures.add((long) saved.getMaDiaChi());
        return saved;
    }

    private Coupon taoCoupon(String ma, int soLuongToiDa) {
        Coupon saved = new TransactionTemplate(txManager).execute(status -> {
            Coupon coupon = new Coupon();
            coupon.setMa(ma);
            coupon.setLoai(LoaiGiamGia.FIXED);
            coupon.setGiaTriGiam(1000);
            coupon.setGiaTriToiThieu(0);
            coupon.setSoLuongToiDa(soLuongToiDa);
            coupon.setDaSuDung(0);
            coupon.setIsActive(true);
            return couponRepository.saveAndFlush(coupon);
        });
        assertThat(saved).isNotNull();
        couponFixtures.add((long) saved.getMaCoupon());
        return saved;
    }

    private List<ResponseEntity<?>> chayDongThoi(Worker dauTien, Worker thuHai) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch batDau = new CountDownLatch(1);
        try {
            Future<ResponseEntity<?>> first = pool.submit(() -> {
                choBatDau(batDau);
                return dauTien.chay();
            });
            Future<ResponseEntity<?>> second = pool.submit(() -> {
                choBatDau(batDau);
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

    private void choBatDau(CountDownLatch batDau) throws InterruptedException {
        if (!batDau.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Workers did not receive the start signal in time");
        }
    }

    @FunctionalInterface
    private interface Worker {
        ResponseEntity<?> chay() throws Exception;
    }
}
