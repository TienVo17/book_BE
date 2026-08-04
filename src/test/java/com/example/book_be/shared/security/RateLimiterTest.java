package com.example.book_be.shared.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private final RateLimiter rateLimiter = new RateLimiter();

    @Test
    void cho_phep_den_gioi_han_roi_tu_choi() {
        Duration cuaSo = Duration.ofMinutes(5);

        for (int lan = 1; lan <= 3; lan++) {
            assertThat(rateLimiter.choPhep("khoa-a", 3, cuaSo))
                    .as("lan goi thu %d van trong gioi han", lan)
                    .isTrue();
        }

        assertThat(rateLimiter.choPhep("khoa-a", 3, cuaSo)).isFalse();
    }

    @Test
    void cac_khoa_khac_nhau_dem_doc_lap() {
        Duration cuaSo = Duration.ofMinutes(5);
        rateLimiter.choPhep("khoa-a", 1, cuaSo);

        assertThat(rateLimiter.choPhep("khoa-a", 1, cuaSo)).isFalse();
        assertThat(rateLimiter.choPhep("khoa-b", 1, cuaSo)).isTrue();
    }

    @Test
    void cua_so_het_han_thi_reset_bo_dem() throws InterruptedException {
        Duration cuaSoNgan = Duration.ofMillis(50);
        assertThat(rateLimiter.choPhep("khoa-c", 1, cuaSoNgan)).isTrue();
        assertThat(rateLimiter.choPhep("khoa-c", 1, cuaSoNgan)).isFalse();

        Thread.sleep(80);

        assertThat(rateLimiter.choPhep("khoa-c", 1, cuaSoNgan))
                .as("qua cua so thi duoc goi lai")
                .isTrue();
    }

    @Test
    void dat_lai_xoa_bo_dem() {
        Duration cuaSo = Duration.ofMinutes(5);
        rateLimiter.choPhep("khoa-d", 1, cuaSo);
        assertThat(rateLimiter.choPhep("khoa-d", 1, cuaSo)).isFalse();

        rateLimiter.datLai("khoa-d");

        assertThat(rateLimiter.choPhep("khoa-d", 1, cuaSo)).isTrue();
    }

    /** Nhieu request song song khong duoc vuot qua gioi han vi race tren bo dem. */
    @Test
    void dem_chinh_xac_khi_goi_song_song() throws Exception {
        int soLuong = 50;
        int gioiHan = 10;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch batDau = new CountDownLatch(1);
        CountDownLatch ketThuc = new CountDownLatch(soLuong);
        AtomicInteger soLanDuocPhep = new AtomicInteger();

        try {
            for (int i = 0; i < soLuong; i++) {
                executor.submit(() -> {
                    try {
                        batDau.await();
                        if (rateLimiter.choPhep("khoa-race", gioiHan, Duration.ofMinutes(5))) {
                            soLanDuocPhep.incrementAndGet();
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } finally {
                        ketThuc.countDown();
                    }
                });
            }

            batDau.countDown();
            assertThat(ketThuc.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(soLanDuocPhep.get()).isEqualTo(gioiHan);
    }
}
