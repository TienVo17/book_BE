package com.example.book_be.portfolio;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.donhang.domain.ChiTietDonHang;
import com.example.book_be.donhang.domain.DonHang;
import com.example.book_be.donhang.domain.TrangThaiGiaoHang;
import com.example.book_be.donhang.repository.ChiTietDonHangRepository;
import com.example.book_be.donhang.repository.DonHangRepository;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.domain.Quyen;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.repository.QuyenRepository;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.sach.repository.SachRepository;
import com.example.book_be.thongke.service.ThongKeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Don demo do migration sinh ra khong duoc chay vao dashboard quan tri.
 *
 * <p>V12 tao don DA_GIAO cho moi danh gia seed chua co bang chung da mua. {@code don_hang}
 * khong phai bang tro: no la dau vao duy nhat cua doanh thu va bang sach ban chay. Neu
 * khong loai tru, dashboard bao mot con so khong ung voi giao dich nao — roi phase 7 chep
 * chinh con so do vao tai lieu portfolio duoi nhan "so that tu lenh that".
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class ThongKeSeedExclusionIT {

    private static final String MAT_KHAU = "ThongKeSeed@123";
    private static final double DOANH_THU_AO = 987_654_321D;

    @Autowired ThongKeService thongKeService;
    @Autowired DonHangRepository donHangRepository;
    @Autowired ChiTietDonHangRepository chiTietDonHangRepository;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired QuyenRepository quyenRepository;
    @Autowired SachRepository sachRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager txManager;

    private String nguoiDung;
    private int maSach;
    private Integer maDonDemo;

    @BeforeEach
    void provisionFixtures() {
        nguoiDung = taoNguoiDung("thongke-seed-" + System.nanoTime());
        maSach = taoSach();
    }

    @AfterEach
    void cleanupFixtures() {
        DonHangDaGiaoFixture.xoaDon(txManager, donHangRepository, maDonDemo);
        maDonDemo = null;
        xoaNguoiDung(nguoiDung);
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                sachRepository.findById((long) maSach).ifPresent(sachRepository::delete));
    }

    /**
     * Don demo o day co gia tri lon va DA thanh toan — dung tinh huong xau nhat. Neu
     * truy van doanh thu bo sot dieu kien {@code laDonDemo = false}, chenh lech se lo ra
     * ngay lap tuc thay vi lan trong sai so lam tron.
     */
    @Test
    void don_demo_khong_bom_doanh_thu() {
        double truoc = (double) thongKeService.getThongKe().get("totalRevenue");

        maDonDemo = taoDonDemo();

        assertThat((double) thongKeService.getThongKe().get("totalRevenue"))
                .as("doanh thu khong duoc doi vi mot don demo")
                .isEqualTo(truoc);
    }

    @Test
    void don_demo_khong_dem_vao_tong_so_don() {
        long truoc = (long) thongKeService.getThongKe().get("totalOrders");

        maDonDemo = taoDonDemo();

        assertThat((long) thongKeService.getThongKe().get("totalOrders"))
                .as("dashboard khong duoc tu mau thuan: loai don demo khoi doanh thu thi cung phai loai khoi tong so don")
                .isEqualTo(truoc);
    }

    @Test
    void don_demo_khong_xao_lai_bang_ban_chay() {
        maDonDemo = taoDonDemo();

        List<Object[]> topBanChay = chiTietDonHangRepository.findTopBanChay(PageRequest.of(0, 50));

        assertThat(topBanChay.stream().map(row -> (Integer) row[0]))
                .as("sach chi ban duoc qua don demo khong duoc xuat hien trong bang ban chay")
                .doesNotContain(maSach);
    }

    // ---------------------------------------------------------------------

    private int taoDonDemo() {
        return new TransactionTemplate(txManager).execute(status -> {
            NguoiDung user = nguoiDungRepository.findByTenDangNhap(nguoiDung);
            Sach sach = sachRepository.findById((long) maSach).orElseThrow();

            DonHang don = new DonHang();
            don.setNgayTao(new Date());
            don.setDiaChiMuaHang("Demo");
            don.setDiaChiNhanHang("Demo");
            don.setTongTienSanPham(DOANH_THU_AO);
            don.setChiPhiGiaoHang(0);
            don.setChiPhiThanhToan(0);
            don.setTongTien(DOANH_THU_AO);
            don.setHoTen("Demo");
            don.setSoDienThoai("0000000000");
            don.setTrangThaiThanhToan(1);
            don.setTrangThaiGiaoHang(TrangThaiGiaoHang.DA_GIAO.getGiaTri());
            don.setNguoiDung(user);
            don.setLaDonDemo(true);

            ChiTietDonHang dong = new ChiTietDonHang();
            dong.setSoLuong(9999);
            dong.setGiaBan(DOANH_THU_AO);
            dong.setDanhGia(false);
            dong.setSach(sach);
            dong.setDonHang(don);
            don.setDanhSachChiTietDonHang(List.of(dong));

            return donHangRepository.saveAndFlush(don).getMaDonHang();
        });
    }

    private int taoSach() {
        return new TransactionTemplate(txManager).execute(status -> {
            Sach mau = sachRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("can it nhat mot cuon sach seed"));
            Sach sach = new Sach();
            sach.setTenSach("Thong Ke Seed Book " + System.nanoTime());
            sach.setTenTacGia("Fixture");
            sach.setMoTa("Sach fixture cho ThongKeSeedExclusionIT");
            sach.setGiaNiemYet(mau.getGiaNiemYet());
            sach.setGiaBan(mau.getGiaBan());
            sach.setSoLuong(100000);
            sach.setTrungBinhXepHang(0);
            sach.setSoLuotDanhGia(0);
            sach.setIsActive(1);
            return sachRepository.saveAndFlush(sach).getMaSach();
        });
    }

    private String taoNguoiDung(String tenDangNhap) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Quyen quyen = quyenRepository.findByTenQuyen("USER");
            NguoiDung user = new NguoiDung();
            user.setHoDem("ThongKe");
            user.setTen("Fixture");
            user.setTenDangNhap(tenDangNhap);
            user.setMatKhau(passwordEncoder.encode(MAT_KHAU));
            user.setGioiTinh('X');
            user.setEmail(tenDangNhap + "@example.test");
            user.setDaKichHoat(true);
            user.setDanhSachQuyen(List.of(quyen));
            nguoiDungRepository.saveAndFlush(user);
        });
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
