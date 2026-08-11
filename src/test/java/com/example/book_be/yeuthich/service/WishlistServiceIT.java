package com.example.book_be.yeuthich.service;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.sach.repository.SachRepository;
import com.example.book_be.yeuthich.dto.WishlistItemResponse;
import com.example.book_be.yeuthich.repository.SachYeuThichRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfig.class)
class WishlistServiceIT {

    private static final long TIMEOUT_SECONDS = 20;

    @Autowired WishlistService wishlistService;
    @Autowired SachYeuThichRepository sachYeuThichRepository;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired SachRepository sachRepository;
    @Autowired PlatformTransactionManager txManager;

    private final List<Integer> bookFixtures = new ArrayList<>();
    private final List<Long> userFixtures = new ArrayList<>();
    private NguoiDung owner;
    private Sach book;

    @BeforeEach
    void provisionFixtures() {
        String runId = "wishlist-" + System.nanoTime();
        owner = new TransactionTemplate(txManager).execute(status -> {
            NguoiDung user = new NguoiDung();
            user.setHoDem("Wishlist");
            user.setTen("Owner");
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
            sach.setTenSach("Wishlist fixture " + System.nanoTime());
            sach.setTenTacGia("IT Fixture");
            sach.setGiaBan(89000D);
            sach.setGiaNiemYet(99000D);
            sach.setSoLuong(0);
            sach.setIsActive(0);
            return sachRepository.saveAndFlush(sach);
        });
        assertThat(book).isNotNull();
        bookFixtures.add(book.getMaSach());
    }

    @AfterEach
    void cleanupFixtures() {
        SecurityContextHolder.clearContext();
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            sachYeuThichRepository.findByNguoiDung_MaNguoiDung(owner.getMaNguoiDung())
                    .forEach(item -> sachYeuThichRepository.deleteById(
                            (long) item.getMaSachYeuThich()));
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
    void post_delete_lap_lai_deu_idempotent_va_tra_snapshot_phang() throws Exception {
        List<WishlistItemResponse> first = voiOwner(
                () -> wishlistService.ensureBookPresent(book.getMaSach()));
        List<WishlistItemResponse> replay = voiOwner(
                () -> wishlistService.ensureBookPresent(book.getMaSach()));

        assertThat(first).singleElement().satisfies(item -> {
            assertThat(item.maSach()).isEqualTo(book.getMaSach());
            assertThat(item.tenSach()).isEqualTo(book.getTenSach());
            assertThat(item.giaBan()).isEqualTo(book.getGiaBan());
            assertThat(item.hinhAnh()).isEmpty();
        });
        assertThat(replay).isEqualTo(first);
        assertThat(sachYeuThichRepository.findByNguoiDung_MaNguoiDung(
                owner.getMaNguoiDung())).hasSize(1);

        assertThat(voiOwner(() -> wishlistService.ensureBookAbsent(book.getMaSach())))
                .isEmpty();
        assertThat(voiOwner(() -> wishlistService.ensureBookAbsent(book.getMaSach())))
                .isEmpty();
    }

    @Test
    void wishlist_luu_duoc_sach_inactive_hoac_het_hang_nhung_sach_khong_ton_tai_bi_404() throws Exception {
        assertThat(voiOwner(() -> wishlistService.ensureBookPresent(book.getMaSach())))
                .singleElement()
                .extracting(WishlistItemResponse::maSach)
                .isEqualTo(book.getMaSach());

        assertThatThrownBy(() -> voiOwner(() -> wishlistService.ensureBookPresent(Integer.MAX_VALUE)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
                        .isEqualTo(404));
    }

    @Test
    void hai_post_dong_thoi_chi_tao_mot_row() throws Exception {
        List<Object> results = chayDongThoi(
                () -> voiOwner(() -> wishlistService.ensureBookPresent(book.getMaSach())),
                () -> voiOwner(() -> wishlistService.ensureBookPresent(book.getMaSach()))
        );

        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(result -> assertThat((List<?>) result).hasSize(1));
        assertThat(sachYeuThichRepository.findByNguoiDung_MaNguoiDung(
                owner.getMaNguoiDung())).hasSize(1);
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
