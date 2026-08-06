package com.example.book_be.portfolio;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.danhgia.domain.TrangThaiDanhGia;
import com.example.book_be.danhgia.repository.SuDanhGiaRepository;
import com.example.book_be.danhgia.service.DanhGiaService;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.domain.Quyen;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bat bien: {@code sach.trung_binh_xep_hang} va {@code sach.so_luot_danh_gia} luon bang
 * ket qua tinh lai tu cac danh gia dang HIEN_THI — sau MOI thao tac, khong chi sau khi them.
 *
 * <p>Phan phu quan trong nhat la sua va xoa. Chi rieng duong them moi tinh cờ dung ke ca
 * khi thieu flush, vi {@code @GeneratedValue(IDENTITY)} ep INSERT ngay lap tuc de lay id.
 * Mot bo test chi phu duong them se xanh trong khi sua va xoa deu ghi ra so sai.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class DanhGiaAggregateIT {

    private static final String MAT_KHAU = "Aggregate@12345";

    @Autowired DanhGiaService danhGiaService;
    @Autowired SuDanhGiaRepository suDanhGiaRepository;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired QuyenRepository quyenRepository;
    @Autowired SachRepository sachRepository;
    @Autowired com.example.book_be.donhang.repository.DonHangRepository donHangRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager txManager;

    private final List<String> nguoiDungDaTao = new ArrayList<>();
    private final List<Long> danhGiaDaTao = new ArrayList<>();
    private final List<Integer> donDaTao = new ArrayList<>();
    private int maSach;

    /**
     * Dung mot cuon sach RIENG cho moi lan chay thay vi cuon seed dau tien.
     *
     * <p>Cac cuon seed da co san danh gia trong V4, nen moi khang dinh ve gia tri tuyet doi
     * (vi du "trung binh phai bang 5.0") se phu thuoc vao du lieu seed va sai ngay. Sach rieng
     * lam moi con so trong test tro nen tat dinh.
     */
    @BeforeEach
    void provisionFixtures() {
        maSach = new TransactionTemplate(txManager).execute(status -> {
            Sach mau = sachRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("can it nhat mot cuon sach seed"));
            Sach sach = new Sach();
            sach.setTenSach("Aggregate Fixture Book " + System.nanoTime());
            sach.setTenTacGia("Fixture");
            sach.setMoTa("Sach fixture cho DanhGiaAggregateIT");
            sach.setGiaNiemYet(mau.getGiaNiemYet());
            sach.setGiaBan(mau.getGiaBan());
            sach.setSoLuong(100);
            sach.setTrungBinhXepHang(0);
            sach.setSoLuotDanhGia(0);
            sach.setIsActive(1);
            return sachRepository.saveAndFlush(sach).getMaSach();
        });
    }

    @AfterEach
    void cleanupFixtures() {
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                danhGiaDaTao.forEach(id ->
                        suDanhGiaRepository.findById(id).ifPresent(suDanhGiaRepository::delete)));
        danhGiaDaTao.clear();
        donDaTao.forEach(ma -> DonHangDaGiaoFixture.xoaDon(txManager, donHangRepository, ma));
        donDaTao.clear();
        nguoiDungDaTao.forEach(this::xoaNguoiDung);
        nguoiDungDaTao.clear();
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                sachRepository.findById((long) maSach).ifPresent(sachRepository::delete));
    }

    @Test
    void them_danh_gia_cap_nhat_tong_hop() {
        themDanhGia(taoNguoiDung(), 4F);

        khangDinhTongHopKhopThucTe();
    }

    /**
     * Duong de hong nhat. {@code updateReview} sua entity dang duoc quan ly roi goi save
     * (merge, chua sinh SQL). Neu cau tinh lai khong flush truoc, no doc diem CU va so
     * trung binh se ket lai o gia tri sai vinh vien.
     */
    @Test
    void sua_diem_danh_gia_cap_nhat_tong_hop() {
        String nguoiDung = taoNguoiDung();
        Long maDanhGia = themDanhGia(nguoiDung, 1F);

        SuDanhGia thayDoi = new SuDanhGia();
        thayDoi.setNhanXet("Doc lai lan hai, hay hon nhieu");
        thayDoi.setDiemXepHang(5F);
        danhGiaService.updateReview(maDanhGia, thayDoi, maNguoiDungCua(nguoiDung));

        khangDinhTongHopKhopThucTe();
        assertThat(diemTrungBinhHienTai())
                .as("diem trung binh phai theo kip diem da sua, khong giu lai gia tri cu")
                .isEqualTo(5.0);
    }

    /** {@code deleteReview} goi remove, hoan toi flush — cung mot cai bay nhu sua. */
    @Test
    void xoa_danh_gia_cap_nhat_tong_hop() {
        String nguoiDung = taoNguoiDung();
        Long maDanhGia = themDanhGia(nguoiDung, 2F);
        int soLuotTruoc = soLuotHienTai();

        danhGiaService.deleteReview(maDanhGia, maNguoiDungCua(nguoiDung), false);
        danhGiaDaTao.remove(maDanhGia);

        khangDinhTongHopKhopThucTe();
        assertThat(soLuotHienTai())
                .as("so luot phai giam sau khi xoa")
                .isEqualTo(soLuotTruoc - 1);
    }

    @Test
    void admin_an_danh_gia_cap_nhat_tong_hop() {
        String nguoiDung = taoNguoiDung();
        Long maDanhGia = themDanhGia(nguoiDung, 1F);
        int soLuotTruoc = soLuotHienTai();

        danhGiaService.doiTrangThai(maDanhGia, TrangThaiDanhGia.DA_AN);

        khangDinhTongHopKhopThucTe();
        assertThat(soLuotHienTai())
                .as("danh gia da an khong duoc tinh vao so luot")
                .isEqualTo(soLuotTruoc - 1);
    }

    @Test
    void admin_hien_lai_danh_gia_cap_nhat_tong_hop() {
        String nguoiDung = taoNguoiDung();
        Long maDanhGia = themDanhGia(nguoiDung, 3F);
        danhGiaService.doiTrangThai(maDanhGia, TrangThaiDanhGia.DA_AN);
        int soLuotSauKhiAn = soLuotHienTai();

        danhGiaService.doiTrangThai(maDanhGia, TrangThaiDanhGia.HIEN_THI);

        khangDinhTongHopKhopThucTe();
        assertThat(soLuotHienTai()).isEqualTo(soLuotSauKhiAn + 1);
    }

    @Test
    void hai_luong_cung_them_danh_gia_khong_lam_lech_tong_hop() throws Exception {
        String nguoiDungA = taoNguoiDung();
        String nguoiDungB = taoNguoiDung();
        CountDownLatch batDau = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            var luongA = pool.submit(() -> {
                batDau.await();
                return themDanhGia(nguoiDungA, 5F);
            });
            var luongB = pool.submit(() -> {
                batDau.await();
                return themDanhGia(nguoiDungB, 1F);
            });
            batDau.countDown();
            luongA.get(30, TimeUnit.SECONDS);
            luongB.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        khangDinhTongHopKhopThucTe();
    }

    /** Duong sua chua: lam lech co y roi khang dinh tinh lai toan bo dua ve dung. */
    @Test
    void tinh_lai_tat_ca_sua_duoc_du_lieu_da_lech() {
        themDanhGia(taoNguoiDung(), 4F);
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Sach sach = sachRepository.findById((long) maSach).orElseThrow();
            sach.setTrungBinhXepHang(0.123);
            sach.setSoLuotDanhGia(999);
            sachRepository.saveAndFlush(sach);
        });

        danhGiaService.tinhLaiTongHopTatCa();

        khangDinhTongHopKhopThucTe();
    }

    // ---------------------------------------------------------------------

    /** So sanh gia tri ghi san voi ket qua tinh truc tiep tu cac dong danh gia that. */
    private void khangDinhTongHopKhopThucTe() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            List<SuDanhGia> hienThi = suDanhGiaRepository.findAll().stream()
                    .filter(d -> d.getSach() != null && d.getSach().getMaSach() == maSach)
                    .filter(d -> d.getTrangThai() == TrangThaiDanhGia.HIEN_THI)
                    .toList();
            double trungBinhThucTe = hienThi.isEmpty() ? 0
                    : hienThi.stream().mapToDouble(SuDanhGia::getDiemXepHang).average().orElse(0);

            Sach sach = sachRepository.findById((long) maSach).orElseThrow();
            assertThat(sach.getSoLuotDanhGia())
                    .as("so luot ghi san phai bang so dong HIEN_THI that")
                    .isEqualTo(hienThi.size());
            assertThat(sach.getTrungBinhXepHang())
                    .as("diem ghi san phai bang trung binh tinh lai")
                    .isEqualTo(trungBinhThucTe, org.assertj.core.data.Offset.offset(0.0001));
        });
    }

    private double diemTrungBinhHienTai() {
        return new TransactionTemplate(txManager).execute(status ->
                sachRepository.findById((long) maSach).orElseThrow().getTrungBinhXepHang());
    }

    private int soLuotHienTai() {
        return new TransactionTemplate(txManager).execute(status ->
                sachRepository.findById((long) maSach).orElseThrow().getSoLuotDanhGia());
    }

    /**
     * Tu phase 2, {@code addReview} doi mot don DA_GIAO chua cuon sach do. Fixture phai
     * dung don truoc, neu khong moi test o day chi con do 403.
     */
    private Long themDanhGia(String tenDangNhap, float diem) {
        int maDonHang = DonHangDaGiaoFixture.taoDonDaGiao(txManager, donHangRepository,
                nguoiDungRepository, sachRepository, tenDangNhap, maSach);
        synchronized (donDaTao) {
            donDaTao.add(maDonHang);
        }
        SuDanhGia daTao = danhGiaService.addReview(
                "Danh gia fixture cho " + tenDangNhap, diem, maNguoiDungCua(tenDangNhap), (long) maSach);
        Long id = daTao.getMaDanhGia();
        synchronized (danhGiaDaTao) {
            danhGiaDaTao.add(id);
        }
        return id;
    }

    private Long maNguoiDungCua(String tenDangNhap) {
        return new TransactionTemplate(txManager).execute(status ->
                (long) nguoiDungRepository.findByTenDangNhap(tenDangNhap).getMaNguoiDung());
    }

    private String taoNguoiDung() {
        String tenDangNhap = "aggregate-" + System.nanoTime();
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Quyen quyen = quyenRepository.findByTenQuyen("USER");
            NguoiDung user = new NguoiDung();
            user.setHoDem("Aggregate");
            user.setTen("Fixture");
            user.setTenDangNhap(tenDangNhap);
            user.setMatKhau(passwordEncoder.encode(MAT_KHAU));
            user.setGioiTinh('X');
            user.setEmail(tenDangNhap + "@example.test");
            user.setDaKichHoat(true);
            user.setDanhSachQuyen(List.of(quyen));
            nguoiDungRepository.saveAndFlush(user);
        });
        synchronized (nguoiDungDaTao) {
            nguoiDungDaTao.add(tenDangNhap);
        }
        return tenDangNhap;
    }

    private void xoaNguoiDung(String tenDangNhap) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            NguoiDung user = nguoiDungRepository.findByTenDangNhap(tenDangNhap);
            if (user != null) {
                nguoiDungRepository.deleteById((long) user.getMaNguoiDung());
            }
        });
    }
}
