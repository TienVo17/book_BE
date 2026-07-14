package com.example.book_be.donhang.service;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.sach.repository.SachRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Integration test cho tru kho nguyen tu tren MySQL Testcontainers. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfig.class)
class TruKhoIT {

    private static final int MA_SACH = 1;
    private static final long TIMEOUT_SECONDS = 20;

    @Autowired SachRepository sachRepository;
    @Autowired PlatformTransactionManager txManager;

    private Integer tonKhoBanDau;

    @AfterEach
    void khoiPhucTonKho() {
        if (tonKhoBanDau != null) {
            datTonKho(MA_SACH, tonKhoBanDau);
            tonKhoBanDau = null;
        }
    }

    @Test
    void tru_kho_du_thi_giam_dung() {
        datTonKho(MA_SACH, 5);
        assertThat(truTrongTransaction(MA_SACH, 2)).isEqualTo(1);
        assertThat(tonKho(MA_SACH)).isEqualTo(3);
    }

    @Test
    void tru_kho_khong_du_thi_tra_ve_0_va_khong_doi() {
        datTonKho(MA_SACH, 1);
        assertThat(truTrongTransaction(MA_SACH, 2)).isEqualTo(0);
        assertThat(tonKho(MA_SACH)).isEqualTo(1);
    }

    @Test
    void hai_thread_tru_song_song_ton_1_chi_1_thanh_cong() throws Exception {
        datTonKho(MA_SACH, 1);
        CountDownLatch daDocTonKho = new CountDownLatch(2);

        List<Integer> results = chayDongThoi(
                () -> truSauKhiDocCungTonKho(MA_SACH, 1, daDocTonKho),
                () -> truSauKhiDocCungTonKho(MA_SACH, 1, daDocTonKho));

        assertThat(results).containsExactlyInAnyOrder(1, 0);
        assertThat(tonKho(MA_SACH)).as("ton kho cuoi = 0").isZero();
    }

    @Test
    void hoan_kho_cong_lai_dung() {
        datTonKho(MA_SACH, 3);
        new TransactionTemplate(txManager).executeWithoutResult(
                status -> sachRepository.hoanKho(MA_SACH, 2, Integer.MAX_VALUE - 2));
        assertThat(tonKho(MA_SACH)).isEqualTo(5);
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
        Integer updated = new TransactionTemplate(txManager)
                .execute(status -> sachRepository.truKhoNeuDu(maSach, soLuong));
        return updated == null ? 0 : updated;
    }

    private int truSauKhiDocCungTonKho(
            int maSach,
            int soLuong,
            CountDownLatch daDocTonKho) {
        Integer updated = new TransactionTemplate(txManager).execute(status -> {
            Sach staleSach = sachRepository.findById((long) maSach).orElseThrow();
            assertThat(staleSach.getSoLuong()).isEqualTo(1);
            daDocTonKho.countDown();
            choHangRao(daDocTonKho, "Workers did not load the same stock in time");
            return sachRepository.truKhoNeuDu(maSach, soLuong);
        });
        return updated == null ? 0 : updated;
    }

    private void datTonKho(int maSach, int giaTri) {
        if (tonKhoBanDau == null) {
            tonKhoBanDau = tonKho(maSach);
        }
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Sach sach = sachRepository.findById((long) maSach).orElseThrow();
            sach.setSoLuong(giaTri);
            sachRepository.saveAndFlush(sach);
        });
    }

    private int tonKho(int maSach) {
        return sachRepository.findById((long) maSach).orElseThrow().getSoLuong();
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

    @FunctionalInterface
    private interface Worker {
        int chay() throws Exception;
    }
}
