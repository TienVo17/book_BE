package com.example.book_be.giohang.service;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.giohang.domain.GioHang;
import com.example.book_be.giohang.domain.GioHangMergeReceipt;
import com.example.book_be.giohang.dto.CartItemRequest;
import com.example.book_be.giohang.dto.CartMergeRequest;
import com.example.book_be.giohang.dto.CartMergeResponse;
import com.example.book_be.giohang.repository.GioHangMergeReceiptRepository;
import com.example.book_be.giohang.repository.GioHangRepository;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.sach.repository.SachRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfig.class)
class CartMergeIT {

    private static final long TIMEOUT_SECONDS = 20;

    @Autowired CartService cartService;
    @Autowired GioHangRepository gioHangRepository;
    @Autowired GioHangMergeReceiptRepository mergeReceiptRepository;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired SachRepository sachRepository;
    @Autowired PlatformTransactionManager txManager;

    private final List<Long> receiptFixtures = new ArrayList<>();
    private final List<Integer> bookFixtures = new ArrayList<>();
    private final List<Long> userFixtures = new ArrayList<>();
    private NguoiDung owner;
    private Sach book;

    @BeforeEach
    void provisionFixtures() {
        String runId = "cart-merge-" + System.nanoTime();
        owner = new TransactionTemplate(txManager).execute(status -> {
            NguoiDung user = new NguoiDung();
            user.setHoDem("Cart");
            user.setTen("Merge");
            user.setTenDangNhap(runId);
            user.setMatKhau("unused-in-this-it");
            user.setGioiTinh('X');
            user.setEmail(runId + "@example.test");
            user.setDaKichHoat(true);
            return nguoiDungRepository.saveAndFlush(user);
        });
        assertThat(owner).isNotNull();
        userFixtures.add((long) owner.getMaNguoiDung());

        book = new TransactionTemplate(txManager).execute(status -> {
            Sach sach = new Sach();
            sach.setTenSach("Cart merge fixture " + System.nanoTime());
            sach.setTenTacGia("IT Fixture");
            sach.setGiaBan(10000D);
            sach.setGiaNiemYet(12000D);
            sach.setSoLuong(100);
            sach.setIsActive(1);
            return sachRepository.saveAndFlush(sach);
        });
        assertThat(book).isNotNull();
        bookFixtures.add(book.getMaSach());
    }

    @AfterEach
    void cleanupFixtures() {
        SecurityContextHolder.clearContext();
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            for (GioHangMergeReceipt receipt : mergeReceiptRepository.findAll()) {
                if (receipt.getNguoiDung().getMaNguoiDung() == owner.getMaNguoiDung()) {
                    receiptFixtures.add(receipt.getMaGioHangMergeReceipt());
                }
            }
            for (Long receiptId : receiptFixtures) {
                mergeReceiptRepository.deleteById(receiptId);
            }
            gioHangRepository.deleteGioHangByMaNguoiDung(owner.getMaNguoiDung());
            for (Integer maSach : bookFixtures) {
                if (sachRepository.existsById(maSach.longValue())) {
                    sachRepository.deleteById(maSach.longValue());
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
    void hai_add_dong_thoi_khong_tao_duplicate_hoac_lost_update() throws Exception {
        List<Object> results = chayDongThoi(
                () -> voiOwner(() -> cartService.addItem(
                        new CartItemRequest(book.getMaSach(), 1))),
                () -> voiOwner(() -> cartService.addItem(
                        new CartItemRequest(book.getMaSach(), 1)))
        );

        assertThat(results).hasSize(2);
        List<GioHang> lines = gioHangRepository.findByMaNguoiDung(owner.getMaNguoiDung());
        assertThat(lines).singleElement().satisfies(line -> {
            assertThat(line.getSach().getMaSach()).isEqualTo(book.getMaSach());
            assertThat(line.getSoLuong()).isEqualTo(2);
        });
    }

    @Test
    void hai_merge_dong_thoi_cung_key_chi_cong_guest_quantity_mot_lan() throws Exception {
        voiOwner(() -> cartService.addItem(new CartItemRequest(book.getMaSach(), 1)));
        CartMergeRequest request = new CartMergeRequest(
                List.of(new CartItemRequest(book.getMaSach(), 2)));
        String key = "parallel-merge-" + System.nanoTime();

        List<Object> results = chayDongThoi(
                () -> voiOwner(() -> cartService.mergeGuestCart(request, key)),
                () -> voiOwner(() -> cartService.mergeGuestCart(request, key))
        );

        assertThat(results).hasSize(2);
        assertThat(results.get(0)).isEqualTo(results.get(1));
        assertThat(results.get(0)).isInstanceOf(CartMergeResponse.class);
        assertThat(gioHangRepository.findByMaNguoiDung(owner.getMaNguoiDung()))
                .singleElement()
                .extracting(GioHang::getSoLuong)
                .isEqualTo(3);
        assertThat(mergeReceiptRepository.findAll().stream()
                .filter(receipt -> receipt.getNguoiDung().getMaNguoiDung()
                        == owner.getMaNguoiDung())
                .filter(receipt -> key.equals(receipt.getIdempotencyKey())))
                .hasSize(1);
    }

    @Test
    void hai_merge_dong_thoi_cung_key_payload_khac_chi_mot_ben_mutation() throws Exception {
        voiOwner(() -> cartService.addItem(new CartItemRequest(book.getMaSach(), 1)));
        String key = "parallel-conflict-" + System.nanoTime();
        CartMergeRequest first = new CartMergeRequest(
                List.of(new CartItemRequest(book.getMaSach(), 2)));
        CartMergeRequest second = new CartMergeRequest(
                List.of(new CartItemRequest(book.getMaSach(), 5)));

        List<Object> results = chayDongThoiGiuLoi(
                () -> voiOwner(() -> cartService.mergeGuestCart(first, key)),
                () -> voiOwner(() -> cartService.mergeGuestCart(second, key))
        );

        assertThat(results).hasSize(2);
        assertThat(results).filteredOn(CartMergeResponse.class::isInstance).hasSize(1);
        assertThat(results).filteredOn(ResponseStatusException.class::isInstance)
                .singleElement()
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        assertThat(gioHangRepository.findByMaNguoiDung(owner.getMaNguoiDung()))
                .singleElement()
                .extracting(GioHang::getSoLuong)
                .isIn(3, 6);
        assertThat(mergeReceiptRepository.findAll().stream()
                .filter(receipt -> receipt.getNguoiDung().getMaNguoiDung()
                        == owner.getMaNguoiDung())
                .filter(receipt -> key.equals(receipt.getIdempotencyKey())))
                .hasSize(1);
    }

    private <T> T voiOwner(Worker<T> worker) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        owner.getTenDangNhap(), null, List.of()));
        try {
            return worker.chay();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private List<Object> chayDongThoi(Worker<?> firstWorker, Worker<?> secondWorker)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = pool.submit(() -> {
                choBatDau(start);
                return firstWorker.chay();
            });
            Future<?> second = pool.submit(() -> {
                choBatDau(start);
                return secondWorker.chay();
            });
            start.countDown();
            return List.of(
                    first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
    }

    private List<Object> chayDongThoiGiuLoi(
            Worker<?> firstWorker,
            Worker<?> secondWorker
    ) throws Exception {
        return chayDongThoi(
                () -> chayVaGiuLoi(firstWorker),
                () -> chayVaGiuLoi(secondWorker));
    }

    private Object chayVaGiuLoi(Worker<?> worker) {
        try {
            return worker.chay();
        } catch (Exception exception) {
            return exception;
        }
    }

    private void choBatDau(CountDownLatch start) throws InterruptedException {
        if (!start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Workers did not receive the start signal in time");
        }
    }

    @FunctionalInterface
    private interface Worker<T> {
        T chay() throws Exception;
    }
}
