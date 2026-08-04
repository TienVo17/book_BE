package com.example.book_be.shared.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gioi han so lan goi cho cac endpoint nhay cam, theo tung khoa (IP hoac IP+dinh danh).
 *
 * Pham vi co y gioi han: bo dem nam trong bo nho cua tung process. Voi mot instance thi day la
 * bien phap chong lam dung co that; khi scale nhieu instance, gioi han hieu dung se nhan len theo
 * so instance. Do la danh doi da biet — thay the bang Redis khi thuc su chay nhieu instance.
 *
 * Map tu don dep khi mot cua so het han duoc truy cap lai, va co gioi han so khoa de mot ke tan
 * cong khong the lam day bo nho bang cach doi khoa lien tuc.
 */
@Component
public class RateLimiter {

    private static final int SO_KHOA_TOI_DA = 10_000;

    private final Map<String, CuaSo> cacCuaSo = new ConcurrentHashMap<>();

    /**
     * @return {@code true} neu request duoc phep; {@code false} neu da vuot gioi han.
     */
    public boolean choPhep(String khoa, int soLanToiDa, Duration cuaSoThoiGian) {
        long bayGio = System.currentTimeMillis();
        long doDaiCuaSo = cuaSoThoiGian.toMillis();

        if (cacCuaSo.size() > SO_KHOA_TOI_DA) {
            cacCuaSo.entrySet().removeIf(entry -> entry.getValue().daHetHan(bayGio, doDaiCuaSo));
        }

        CuaSo cuaSo = cacCuaSo.compute(khoa, (ignored, hienTai) -> {
            if (hienTai == null || hienTai.daHetHan(bayGio, doDaiCuaSo)) {
                return new CuaSo(bayGio);
            }
            return hienTai;
        });

        return cuaSo.soLan.incrementAndGet() <= soLanToiDa;
    }

    /** Xoa bo dem sau khi mot thao tac thanh cong (vi du dang nhap dung). */
    public void datLai(String khoa) {
        cacCuaSo.remove(khoa);
    }

    private static final class CuaSo {
        private final long thoiDiemBatDau;
        private final AtomicInteger soLan = new AtomicInteger();

        private CuaSo(long thoiDiemBatDau) {
            this.thoiDiemBatDau = thoiDiemBatDau;
        }

        private boolean daHetHan(long bayGio, long doDaiCuaSo) {
            return bayGio - thoiDiemBatDau >= doDaiCuaSo;
        }
    }
}
