package com.example.book_be.sach.service;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.donhang.domain.ChiTietDonHang;
import com.example.book_be.donhang.domain.DonHang;
import com.example.book_be.donhang.domain.TrangThaiGiaoHang;
import com.example.book_be.donhang.dto.CheckoutOrderRequest;
import com.example.book_be.donhang.dto.CheckoutOrderResponse;
import com.example.book_be.donhang.repository.ChiTietDonHangRepository;
import com.example.book_be.donhang.repository.DonHangRepository;
import com.example.book_be.donhang.repository.LichSuTrangThaiDonHangRepository;
import com.example.book_be.donhang.service.DonHangHuyService;
import com.example.book_be.donhang.service.OrderService;
import com.example.book_be.giohang.dto.CartItemRequest;
import com.example.book_be.nguoidung.domain.DiaChiGiaoHang;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.repository.DiaChiGiaoHangRepository;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.sach.domain.TheLoai;
import com.example.book_be.sach.dto.SachAdminUpsertBo;
import com.example.book_be.sach.dto.SachThongTinChiTietBo;
import com.example.book_be.sach.dto.SachTonKhoResponse;
import com.example.book_be.sach.repository.SachRepository;
import com.example.book_be.sach.repository.TheLoaiRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Inventory contracts against a real MySQL database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfig.class)
class SachTonKhoIT {

    private static final long TIMEOUT_SECONDS = 20;

    @Autowired SachRepository sachRepository;
    @Autowired SachService sachService;
    @Autowired DonHangHuyService donHangHuyService;
    @Autowired DonHangRepository donHangRepository;
    @Autowired ChiTietDonHangRepository chiTietDonHangRepository;
    @Autowired LichSuTrangThaiDonHangRepository lichSuRepository;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired DiaChiGiaoHangRepository diaChiGiaoHangRepository;
    @Autowired TheLoaiRepository theLoaiRepository;
    @Autowired OrderService orderService;
    @Autowired PlatformTransactionManager txManager;

    private final List<Integer> sachFixtures = new CopyOnWriteArrayList<>();
    private final List<Long> donHangFixtures = new CopyOnWriteArrayList<>();
    private final List<Long> diaChiFixtures = new CopyOnWriteArrayList<>();

    @AfterEach
    void cleanupFixtures() {
        SecurityContextHolder.clearContext();
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
        });
    }

    @Test
    void metadata_flush_tu_managed_entity_cu_khong_duoc_ghi_de_stock_atomic_moi() {
        Sach fixture = taoSach(10);
        int maSach = fixture.getMaSach();
        TransactionTemplate metadataTx = new TransactionTemplate(txManager);
        TransactionTemplate stockTx = new TransactionTemplate(txManager);
        stockTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        metadataTx.executeWithoutResult(outer -> {
            Sach managedSach = sachRepository.findById((long) maSach).orElseThrow();
            stockTx.executeWithoutResult(inner ->
                    assertThat(sachRepository.truKhoNeuDu(maSach, 2)).isEqualTo(1));
            managedSach.setTenTacGia("Metadata regression updated");
        });

        assertThat(tonKho(maSach)).as("metadata flush must not restore stale stock 10").isEqualTo(8);
        assertThat(sachRepository.findById((long) maSach).orElseThrow().getTenTacGia())
                .isEqualTo("Metadata regression updated");
    }

    @Test
    void create_chap_nhan_ton_ban_dau_con_metadata_update_bo_qua_ton_va_giu_day_du_metadata() throws Exception {
        SachAdminUpsertBo create = boMoi("Create stock fixture", 5);
        create.setListImageStr(List.of("https://example.com/stock-old.jpg"));
        Sach created = sachService.save(create);
        sachFixtures.add(created.getMaSach());
        assertThat(tonKho(created.getMaSach())).isEqualTo(5);

        TheLoai theLoai = theLoaiRepository.findAll().stream().findFirst().orElseThrow();
        SachAdminUpsertBo update = boTu(created);
        update.setSoLuongTon(null);
        update.setTenTacGia("Metadata accepts null stock");
        update.setMoTaChiTiet("<p>Updated safe description</p>");
        update.setMaTheLoaiList(List.of(theLoai.getMaTheLoai()));
        update.setListImageStr(List.of("https://example.com/stock-new.jpg"));
        SachThongTinChiTietBo chiTiet = new SachThongTinChiTietBo();
        chiTiet.setNhaXuatBan("Inventory publisher");
        chiTiet.setSoTrang(321);
        update.setChiTiet(chiTiet);
        sachService.update(update);

        update.setSoLuongTon(-1);
        update.setTenTacGia("Metadata accepts negative stock");
        sachService.update(update);

        MetadataSnapshot snapshot = new TransactionTemplate(txManager).execute(status -> {
            Sach actual = sachRepository.findById((long) created.getMaSach()).orElseThrow();
            return new MetadataSnapshot(
                    actual.getSoLuong(),
                    actual.getTenTacGia(),
                    actual.getMoTaChiTiet(),
                    actual.getListTheLoai().stream().map(TheLoai::getMaTheLoai).toList(),
                    actual.getListHinhAnh().stream().map(image -> image.getUrlHinh()).toList(),
                    actual.getThongTinChiTiet().getNhaXuatBan(),
                    actual.getThongTinChiTiet().getSoTrang());
        });
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.soLuong()).isEqualTo(5);
        assertThat(snapshot.tenTacGia()).isEqualTo("Metadata accepts negative stock");
        assertThat(snapshot.moTaChiTiet()).isEqualTo("<p>Updated safe description</p>");
        assertThat(snapshot.maTheLoai()).containsExactly(theLoai.getMaTheLoai());
        assertThat(snapshot.imageUrls()).containsExactly("https://example.com/stock-new.jpg");
        assertThat(snapshot.nhaXuatBan()).isEqualTo("Inventory publisher");
        assertThat(snapshot.soTrang()).isEqualTo(321);
    }

    @Test
    void tru_kho_tu_choi_so_luong_khong_duong_va_khong_mint_stock() {
        Sach fixture = taoSach(8);
        int maSach = fixture.getMaSach();

        assertThat(truTrongTransaction(maSach, 0)).isZero();
        assertThat(truTrongTransaction(maSach, -1)).isZero();
        assertThat(tonKho(maSach)).isEqualTo(8);
    }

    @Test
    void checkout_gom_duplicate_quantities_vuot_int_phai_400_rollback_va_khong_tang_kho() throws Exception {
        Sach fixture = taoSach(8);
        CheckoutOrderRequest request = checkoutRequest(fixture.getMaSach(), Integer.MAX_VALUE,
                new CartItemRequest(fixture.getMaSach(), 1));

        assertThatThrownBy(() -> voiNguoiDung("user1", () -> orderService.saveOrUpdate(request)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(tonKho(fixture.getMaSach())).isEqualTo(8);
    }

    @Test
    void checkout_metadata_stale_va_admin_delta_dung_service_dat_ket_qua_13() throws Exception {
        Sach fixture = taoSach(10);
        CheckoutOrderResponse checkout = voiNguoiDung("user1",
                () -> orderService.saveOrUpdate(checkoutRequest(fixture.getMaSach(), 2)));
        donHangFixtures.add(checkout.getMaDonHang().longValue());
        assertThat(tonKho(fixture.getMaSach())).isEqualTo(8);

        SachAdminUpsertBo staleMetadata = boTu(fixture);
        staleMetadata.setSoLuongTon(10);
        staleMetadata.setTenTacGia("Metadata after checkout");
        sachService.update(staleMetadata);
        assertThat(tonKho(fixture.getMaSach())).isEqualTo(8);

        SachTonKhoResponse response = sachService.dieuChinhTonKho((long) fixture.getMaSach(), 5);
        assertThat(response.soLuongTon()).isEqualTo(13);
        assertThat(tonKho(fixture.getMaSach())).isEqualTo(13);
    }

    @Test
    void admin_delta_kiem_tra_cac_bien_int_va_tra_stock_authoritative() {
        Sach fixture = taoSach(8);
        long maSach = fixture.getMaSach();

        assertThat(sachService.dieuChinhTonKho(maSach, -3).soLuongTon()).isEqualTo(5);
        assertThatThrownBy(() -> sachService.dieuChinhTonKho(maSach, Integer.MIN_VALUE))
                .isInstanceOf(StockAdjustmentConflictException.class);
        datTonKho(fixture.getMaSach(), 0);
        assertThat(sachService.dieuChinhTonKho(maSach, Integer.MAX_VALUE).soLuongTon())
                .isEqualTo(Integer.MAX_VALUE);
        assertThatThrownBy(() -> sachService.dieuChinhTonKho(maSach, 1))
                .isInstanceOf(StockAdjustmentConflictException.class);
        assertThat(tonKho(fixture.getMaSach())).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void hai_admin_delta_duong_dong_thoi_khong_mat_update() throws Exception {
        Sach fixture = taoSach(10);
        long maSach = fixture.getMaSach();
        CountDownLatch daDocTonKho = new CountDownLatch(2);

        List<Integer> results = chayDongThoi(
                () -> dieuChinhSauKhiDocCungTonKho(maSach, 10, 1, daDocTonKho),
                () -> dieuChinhSauKhiDocCungTonKho(maSach, 10, 1, daDocTonKho));

        assertThat(results).containsOnly(1);
        assertThat(tonKho(fixture.getMaSach())).isEqualTo(12);
    }

    @Test
    void hai_admin_delta_duong_gan_integer_max_chi_mot_duoc_phep() throws Exception {
        Sach fixture = taoSach(Integer.MAX_VALUE - 1);
        long maSach = fixture.getMaSach();
        CountDownLatch daDocTonKho = new CountDownLatch(2);

        List<Integer> results = chayDongThoi(
                () -> dieuChinhSauKhiDocCungTonKho(
                        maSach, Integer.MAX_VALUE - 1, 1, daDocTonKho),
                () -> dieuChinhSauKhiDocCungTonKho(
                        maSach, Integer.MAX_VALUE - 1, 1, daDocTonKho));

        assertThat(results).containsExactlyInAnyOrder(1, 0);
        assertThat(tonKho(fixture.getMaSach())).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void checkout_va_admin_delta_dong_thoi_goi_dung_service_va_dat_tong_chinh_xac() throws Exception {
        Sach fixture = taoSach(10);
        CheckoutOrderRequest request = checkoutRequest(fixture.getMaSach(), 2);
        CountDownLatch daDocTonKho = new CountDownLatch(2);

        List<Integer> results = chayDongThoi(
                () -> thucThiSauKhiDocCungTonKho(
                        fixture.getMaSach(), 10, daDocTonKho, () -> {
                            CheckoutOrderResponse checkout = voiNguoiDung(
                                    "user1", () -> orderService.saveOrUpdate(request));
                            donHangFixtures.add(checkout.getMaDonHang().longValue());
                            return 1;
                        }),
                () -> thucThiSauKhiDocCungTonKho(
                        fixture.getMaSach(), 10, daDocTonKho, () -> {
                            sachService.dieuChinhTonKho((long) fixture.getMaSach(), 5);
                            return 1;
                        }));

        assertThat(results).containsOnly(1);
        assertThat(tonKho(fixture.getMaSach())).as("checkout -2 and delta +5").isEqualTo(13);
    }

    @Test
    void cancel_restore_va_admin_delta_giam_dong_thoi_goi_dung_service() throws Exception {
        Sach fixture = taoSach(10);
        Long maDonHang = taoDonHangHuy(fixture.getMaSach(), 2);
        CountDownLatch daDocTonKho = new CountDownLatch(2);

        List<Integer> results = chayDongThoi(
                () -> thucThiSauKhiDocCungTonKho(
                        fixture.getMaSach(), 10, daDocTonKho, () -> {
                            donHangHuyService.huyDon(
                                    maDonHang,
                                    nguoiDungRepository.findByTenDangNhap("admin"),
                                    true);
                            return 1;
                        }),
                () -> thucThiSauKhiDocCungTonKho(
                        fixture.getMaSach(), 10, daDocTonKho, () -> {
                            sachService.dieuChinhTonKho((long) fixture.getMaSach(), -1);
                            return 1;
                        }));

        assertThat(results).containsOnly(1);
        assertThat(tonKho(fixture.getMaSach())).as("cancel restore +2 and admin delta -1").isEqualTo(11);
    }

    @Test
    void hoan_huy_don_vuot_integer_max_phai_rollback_ca_transaction() {
        Sach fixture = taoSach(Integer.MAX_VALUE);
        Long maDonHang = taoDonHangHuy(fixture.getMaSach(), 1);
        NguoiDung admin = nguoiDungRepository.findByTenDangNhap("admin");

        assertThatThrownBy(() -> donHangHuyService.huyDon(maDonHang, admin, true))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        assertThat(tonKho(fixture.getMaSach())).isEqualTo(Integer.MAX_VALUE);
        assertThat(donHangRepository.findById(maDonHang).orElseThrow().getTrangThaiGiaoHang())
                .isEqualTo(TrangThaiGiaoHang.CHO_XU_LY.getGiaTri());
        assertThat(lichSuRepository.findByMaDonHangOrderByThoiDiemAsc(maDonHang.intValue())).isEmpty();
    }

    private int dieuChinhMotHoacConflict(long maSach, int delta) {
        try {
            sachService.dieuChinhTonKho(maSach, delta);
            return 1;
        } catch (StockAdjustmentConflictException expected) {
            return 0;
        }
    }

    private int dieuChinhSauKhiDocCungTonKho(
            long maSach,
            int tonKhoMongDoi,
            int delta,
            CountDownLatch daDocTonKho) {
        try {
            Integer result = new TransactionTemplate(txManager).execute(status -> {
                Sach staleSach = sachRepository.findById(maSach).orElseThrow();
                assertThat(staleSach.getSoLuong()).isEqualTo(tonKhoMongDoi);
                daDocTonKho.countDown();
                choHangRao(daDocTonKho, "Workers did not load the same stock in time");
                sachService.dieuChinhTonKho(maSach, delta);
                return 1;
            });
            return result == null ? 0 : result;
        } catch (StockAdjustmentConflictException expected) {
            return 0;
        }
    }

    private int thucThiSauKhiDocCungTonKho(
            int maSach,
            int tonKhoMongDoi,
            CountDownLatch daDocTonKho,
            Worker action) throws Exception {
        Integer result = new TransactionTemplate(txManager).execute(status -> {
            Sach staleSach = sachRepository.findById((long) maSach).orElseThrow();
            assertThat(staleSach.getSoLuong()).isEqualTo(tonKhoMongDoi);
            daDocTonKho.countDown();
            choHangRao(daDocTonKho, "Workers did not load the same stock in time");
            try {
                return action.chay();
            } catch (Exception e) {
                throw new WorkerExecutionException(e);
            }
        });
        return result == null ? 0 : result;
    }

    private List<Integer> chayDongThoi(Worker dauTien, Worker thuHai) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch batDau = new CountDownLatch(1);
        try {
            Future<Integer> first = pool.submit(() -> {
                choBatDau(batDau);
                return dauTien.chay();
            });
            Future<Integer> second = pool.submit(() -> {
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

    private int truTrongTransaction(int maSach, int soLuong) {
        Integer updated = new TransactionTemplate(txManager).execute(
                status -> sachRepository.truKhoNeuDu(maSach, soLuong));
        return updated == null ? 0 : updated;
    }

    private void datTonKho(int maSach, int giaTri) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Sach sach = sachRepository.findById((long) maSach).orElseThrow();
            sach.setSoLuong(giaTri);
            sachRepository.saveAndFlush(sach);
        });
    }

    private int tonKho(int maSach) {
        return sachRepository.findSoLuongByMaSach(maSach);
    }

    private Long taoDonHangHuy(int maSach, int soLuong) {
        Long maDonHang = new TransactionTemplate(txManager).execute(status -> {
            Sach sach = sachRepository.findById((long) maSach).orElseThrow();
            DonHang donHang = new DonHang();
            donHang.setNgayTao(new Date());
            donHang.setNguoiDung(nguoiDungRepository.findByTenDangNhap("user1"));
            donHang.setHoTen("Inventory contract");
            donHang.setSoDienThoai("0900000000");
            donHang.setTongTien(100000);
            donHang.setTrangThaiThanhToan(0);
            donHang.setTrangThaiGiaoHang(TrangThaiGiaoHang.CHO_XU_LY.getGiaTri());
            donHangRepository.saveAndFlush(donHang);

            ChiTietDonHang chiTiet = new ChiTietDonHang();
            chiTiet.setDonHang(donHang);
            chiTiet.setSach(sach);
            chiTiet.setSoLuong(soLuong);
            chiTiet.setGiaBan(sach.getGiaBan());
            chiTietDonHangRepository.saveAndFlush(chiTiet);
            return (long) donHang.getMaDonHang();
        });
        if (maDonHang == null) {
            throw new IllegalStateException("Không thể tạo đơn hàng test");
        }
        donHangFixtures.add(maDonHang);
        return maDonHang;
    }

    private CheckoutOrderRequest checkoutRequest(int maSach, int soLuong, CartItemRequest... themItems) {
        DiaChiGiaoHang address = new TransactionTemplate(txManager).execute(status -> {
            NguoiDung user = nguoiDungRepository.findByTenDangNhap("user1");
            DiaChiGiaoHang fixture = new DiaChiGiaoHang();
            fixture.setNguoiDung(user);
            fixture.setHoTen("Inventory contract user");
            fixture.setSoDienThoai("0900000000");
            fixture.setDiaChiDayDu("Inventory contract address");
            fixture.setMacDinh(false);
            return diaChiGiaoHangRepository.saveAndFlush(fixture);
        });
        if (address == null) {
            throw new IllegalStateException("Không thể tạo địa chỉ test");
        }
        diaChiFixtures.add((long) address.getMaDiaChi());

        CopyOnWriteArrayList<CartItemRequest> items = new CopyOnWriteArrayList<>();
        items.add(new CartItemRequest(maSach, soLuong));
        items.addAll(List.of(themItems));
        return new CheckoutOrderRequest(items, address.getMaDiaChi(), "COD", null);
    }

    private <T> T voiNguoiDung(String tenDangNhap, ThrowingSupplier<T> action) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(tenDangNhap, null, List.of()));
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Sach taoSach(int soLuongTon) {
        Sach created = sachService.save(boMoi("Stock fixture", soLuongTon));
        sachFixtures.add(created.getMaSach());
        return created;
    }

    private SachAdminUpsertBo boMoi(String tenSach, int soLuongTon) {
        SachAdminUpsertBo bo = new SachAdminUpsertBo();
        bo.setTenSach(tenSach + " " + System.nanoTime());
        bo.setTenTacGia("Inventory contract author");
        bo.setMoTaChiTiet("Inventory contract description");
        bo.setGiaNiemYet(20000D);
        bo.setGiaBan(10000D);
        bo.setSoLuongTon(soLuongTon);
        bo.setIsActive(1);
        bo.setMaTheLoaiList(List.of());
        bo.setListImageStr(List.of());
        return bo;
    }

    private SachAdminUpsertBo boTu(Sach sach) {
        SachAdminUpsertBo bo = boMoi(sach.getTenSach(), sach.getSoLuong());
        bo.setMaSach(sach.getMaSach());
        bo.setTenSach(sach.getTenSach());
        bo.setTenTacGia(sach.getTenTacGia());
        bo.setMoTaNgan(sach.getMoTaNgan());
        bo.setMoTaChiTiet(sach.getMoTaChiTiet());
        bo.setGiaNiemYet(sach.getGiaNiemYet());
        bo.setGiaBan(sach.getGiaBan());
        bo.setSoLuongTon(sach.getSoLuong());
        bo.setIsActive(sach.getIsActive());
        bo.setMaTheLoaiList(List.of());
        bo.setListImageStr(List.of());
        return bo;
    }

    private void choBatDau(CountDownLatch batDau) throws InterruptedException {
        if (!batDau.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Workers did not receive the start signal in time");
        }
    }

    private void choHangRao(CountDownLatch latch, String message) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message, e);
        }
    }

    private record MetadataSnapshot(
            int soLuong,
            String tenTacGia,
            String moTaChiTiet,
            List<Integer> maTheLoai,
            List<String> imageUrls,
            String nhaXuatBan,
            Integer soTrang) {
    }

    private static class WorkerExecutionException extends RuntimeException {
        WorkerExecutionException(Exception cause) {
            super(cause);
        }
    }

    @FunctionalInterface
    private interface Worker {
        int chay() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
