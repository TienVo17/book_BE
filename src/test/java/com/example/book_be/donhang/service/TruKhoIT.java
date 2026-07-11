package com.example.book_be.donhang.service;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.sach.repository.SachRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test cho tru kho nguyen tu (Testcontainers - MySQL that).
 * KHONG chay local (Docker-in-Docker han che); compile-only, chay tren CI.
 * Race checkout end-to-end duoc phu boi scripts/kiem-tra-ton-kho.sh (bang chung chinh local).
 *
 * Harness 2-thread viet tu dau bang ExecutorService/CountDownLatch (repo khong co template san).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfig.class)
class TruKhoIT {

    @Autowired
    SachRepository sachRepository;

    @Autowired
    PlatformTransactionManager txManager;

    private static final int MA_SACH = 1; // Dac Nhan Tam (seed V4)

    /** Moi lan tru chay trong transaction rieng de mo phong 2 request doc lap. */
    private int truTrongTransaction(int maSach, int soLuong) {
        return new TransactionTemplate(txManager)
                .execute(status -> sachRepository.truKhoNeuDu(maSach, soLuong));
    }

    private void datTonKho(int maSach, int giaTri) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Sach sach = sachRepository.findById((long) maSach).orElseThrow();
            sach.setSoLuong(giaTri);
            sachRepository.saveAndFlush(sach);
        });
    }

    private int tonKho(int maSach) {
        return sachRepository.findById((long) maSach).orElseThrow().getSoLuong();
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

        int soThread = 2;
        CountDownLatch batDau = new CountDownLatch(1);
        CountDownLatch xong = new CountDownLatch(soThread);
        AtomicInteger soThanhCong = new AtomicInteger(0);
        ExecutorService pool = Executors.newFixedThreadPool(soThread);

        for (int i = 0; i < soThread; i++) {
            pool.submit(() -> {
                try {
                    batDau.await();
                    if (truTrongTransaction(MA_SACH, 1) == 1) {
                        soThanhCong.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    xong.countDown();
                }
            });
        }
        batDau.countDown();
        xong.await();
        pool.shutdown();

        assertThat(soThanhCong.get()).as("dung 1 request tru thanh cong").isEqualTo(1);
        assertThat(tonKho(MA_SACH)).as("ton kho cuoi = 0").isEqualTo(0);
    }

    @Test
    void hoan_kho_cong_lai_dung() {
        datTonKho(MA_SACH, 3);
        new TransactionTemplate(txManager).executeWithoutResult(
                status -> sachRepository.hoanKho(MA_SACH, 2));
        assertThat(tonKho(MA_SACH)).isEqualTo(5);
    }
}
