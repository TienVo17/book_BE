package com.example.book_be.nguoidung.service;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bootstrap admin phai dung mot lan va an toan khi nhieu instance khoi dong cung luc.
 * Chay tren MySQL that vi bat bien nam o khoa dong singleton va rang buoc unique cua V10.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfig.class)
class AdminBootstrapIT {

    private static final String MAT_KHAU_HOP_LE = "MatKhauRatDai@2026";

    @Autowired AdminBootstrapService adminBootstrapService;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager txManager;
    @Autowired JdbcTemplate jdbcTemplate;

    /** Container dung chung giua cac test nen phai tra trang thai ve "chua su dung". */
    @BeforeEach
    void resetTrangThaiBootstrap() {
        jdbcTemplate.update("UPDATE `admin_bootstrap_state` SET `da_su_dung` = 0, "
                + "`thoi_diem_su_dung` = NULL, `ten_dang_nhap_da_tao` = NULL WHERE `singleton_id` = 1");
    }

    @Test
    void chi_tao_admin_o_lan_goi_dau_tien() {
        String tenDangNhap = "bootstrap-" + System.nanoTime();
        String tenDangNhapKhac = tenDangNhap + "-khac";

        boolean lanDau = adminBootstrapService.taoAdminNeuChuaSuDung(yeuCau(tenDangNhap));
        boolean lanHai = adminBootstrapService.taoAdminNeuChuaSuDung(yeuCau(tenDangNhapKhac));

        assertThat(lanDau).as("lan dau tien bootstrap thanh cong").isTrue();
        assertThat(lanHai).as("doi dinh danh khac cung khong tao them admin").isFalse();
        assertThat(nguoiDungRepository.existsByTenDangNhap(tenDangNhapKhac)).isFalse();

        NguoiDung admin = nguoiDungRepository.findByTenDangNhap(tenDangNhap);
        assertThat(admin).isNotNull();
        assertThat(admin.getDaKichHoat()).isTrue();
        assertThat(admin.getMatKhau()).isNotEqualTo(MAT_KHAU_HOP_LE);
        assertThat(passwordEncoder.matches(MAT_KHAU_HOP_LE, admin.getMatKhau())).isTrue();
        assertThat(admin.getDanhSachQuyen()).extracting("tenQuyen").containsExactly("ADMIN");

        donDep(tenDangNhap);
    }

    @Test
    void hai_instance_khoi_dong_dong_thoi_chi_tao_mot_admin() throws Exception {
        String tienTo = "bootstrap-race-" + System.nanoTime();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch batDauCungLuc = new CountDownLatch(1);
        AtomicInteger soLanTao = new AtomicInteger();

        try {
            List<Future<?>> ketQua = List.of(
                    executor.submit(() -> chayBootstrap(batDauCungLuc, tienTo + "-a", soLanTao)),
                    executor.submit(() -> chayBootstrap(batDauCungLuc, tienTo + "-b", soLanTao)));

            batDauCungLuc.countDown();
            for (Future<?> future : ketQua) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(soLanTao.get()).as("dung mot admin duoc tao").isEqualTo(1);
        boolean coA = nguoiDungRepository.existsByTenDangNhap(tienTo + "-a");
        boolean coB = nguoiDungRepository.existsByTenDangNhap(tienTo + "-b");
        assertThat(coA ^ coB).as("chi mot trong hai dinh danh ton tai").isTrue();

        donDep(tienTo + "-a");
        donDep(tienTo + "-b");
    }

    @Test
    void tu_choi_cau_hinh_yeu_hoac_dinh_danh_seed() {
        AdminBootstrapProperties properties = new AdminBootstrapProperties();
        properties.setEnabled(true);
        properties.setUsername("admin");
        properties.setEmail("quantri@example.test");
        properties.setPassword(MAT_KHAU_HOP_LE);

        assertThatThrownBy(properties::yeuCauHopLe)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_BOOTSTRAP_USERNAME");

        properties.setUsername("quantri-moi");
        properties.setPassword("ngan");
        assertThatThrownBy(properties::yeuCauHopLe)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_BOOTSTRAP_PASSWORD");

        properties.setPassword(MAT_KHAU_HOP_LE);
        properties.setEmail("");
        assertThatThrownBy(properties::yeuCauHopLe)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_BOOTSTRAP_EMAIL");
    }

    private AdminBootstrapRequest yeuCau(String tenDangNhap) {
        return new AdminBootstrapRequest(tenDangNhap, tenDangNhap + "@example.test", MAT_KHAU_HOP_LE);
    }

    private void chayBootstrap(CountDownLatch batDauCungLuc, String tenDangNhap, AtomicInteger soLanTao) {
        try {
            batDauCungLuc.await();
            if (adminBootstrapService.taoAdminNeuChuaSuDung(yeuCau(tenDangNhap))) {
                soLanTao.incrementAndGet();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private void donDep(String tenDangNhap) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            NguoiDung nguoiDung = nguoiDungRepository.findByTenDangNhap(tenDangNhap);
            if (nguoiDung != null) {
                nguoiDungRepository.deleteById((long) nguoiDung.getMaNguoiDung());
            }
        });
    }
}
