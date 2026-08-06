package com.example.book_be.portfolio;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.danhgia.repository.SuDanhGiaRepository;
import com.example.book_be.donhang.domain.TrangThaiGiaoHang;
import com.example.book_be.donhang.repository.DonHangRepository;
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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Che do Chat: khong ai danh gia duoc cuon sach ma minh chua nhan.
 *
 * <p>Truoc phase nay, bat ky tai khoan nao dang nhap deu danh gia duoc bat ky cuon sach
 * nao — ke ca sach chua tung mua. Diem sao cua mot san pham thuong mai vi the khong
 * mang thong tin gi ve trai nghiem mua hang that.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class VerifiedPurchaseIT {

    private static final String MAT_KHAU = "VerifiedPurchase@123";

    @Autowired TestRestTemplate rest;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired QuyenRepository quyenRepository;
    @Autowired SuDanhGiaRepository suDanhGiaRepository;
    @Autowired SachRepository sachRepository;
    @Autowired DonHangRepository donHangRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager txManager;

    private final List<Integer> donDaTao = new ArrayList<>();
    private String nguoiMua;
    private String nguoiLa;
    private int maSach;

    @BeforeEach
    void provisionFixtures() {
        long runId = System.nanoTime();
        nguoiMua = taoNguoiDung("verified-buyer-" + runId);
        nguoiLa = taoNguoiDung("verified-stranger-" + runId);
        maSach = taoSach();
    }

    @AfterEach
    void cleanupFixtures() {
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                suDanhGiaRepository.findAll().stream()
                        .filter(d -> d.getSach() != null && d.getSach().getMaSach() == maSach)
                        .forEach(suDanhGiaRepository::delete));
        donDaTao.forEach(ma -> DonHangDaGiaoFixture.xoaDon(txManager, donHangRepository, ma));
        donDaTao.clear();
        xoaNguoiDung(nguoiMua);
        xoaNguoiDung(nguoiLa);
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                sachRepository.findById((long) maSach).ifPresent(sachRepository::delete));
    }

    @Test
    void chua_mua_thi_khong_danh_gia_duoc() {
        ResponseEntity<String> response = guiDanhGia(nguoiMua, "Chua mua ma van doi danh gia");

        assertThat(response.getStatusCode().value())
                .as("chua mua la 403, khong phai 200")
                .isEqualTo(403);
        assertThat(response.getBody() == null ? "" : response.getBody())
                .contains("\"code\":\"FORBIDDEN\"");
    }

    @Test
    void mua_roi_nhung_chua_nhan_hang_thi_chua_danh_gia_duoc() {
        donDaTao.add(DonHangDaGiaoFixture.taoDonChuaGiao(txManager, donHangRepository,
                nguoiDungRepository, sachRepository, nguoiMua, maSach, TrangThaiGiaoHang.DANG_GIAO));

        ResponseEntity<String> response = guiDanhGia(nguoiMua, "Hang chua toi ma da danh gia");

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void don_da_huy_khong_mo_khoa_quyen_danh_gia() {
        donDaTao.add(DonHangDaGiaoFixture.taoDonChuaGiao(txManager, donHangRepository,
                nguoiDungRepository, sachRepository, nguoiMua, maSach, TrangThaiGiaoHang.DA_HUY));

        ResponseEntity<String> response = guiDanhGia(nguoiMua, "Don da huy ma van doi danh gia");

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void da_nhan_hang_thi_danh_gia_duoc_va_luu_dung_don() {
        int maDonHang = DonHangDaGiaoFixture.taoDonDaGiao(txManager, donHangRepository,
                nguoiDungRepository, sachRepository, nguoiMua, maSach);
        donDaTao.add(maDonHang);

        ResponseEntity<String> response = guiDanhGia(nguoiMua, "Da nhan hang, sach dung nhu mo ta");

        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("nguoi da nhan hang phai danh gia duoc")
                .isTrue();

        SuDanhGia daLuu = suDanhGiaRepository.findAll().stream()
                .filter(d -> d.getSach() != null && d.getSach().getMaSach() == maSach)
                .findFirst()
                .orElseThrow(() -> new AssertionError("khong tim thay danh gia vua tao"));
        assertThat(daLuu.getMaDonHang())
                .as("danh gia phai tro toi dung don lam bang chung, khong phai NULL")
                .isEqualTo(maDonHang);
    }

    /**
     * Bang chung phai gan voi chinh nguoi danh gia. Neu truy van chi hoi "cuon sach nay
     * da tung duoc giao cho ai do chua" thi mot don cua nguoi khac se mo khoa cho tat ca.
     */
    @Test
    void don_cua_nguoi_khac_khong_mo_khoa_cho_minh() {
        donDaTao.add(DonHangDaGiaoFixture.taoDonDaGiao(txManager, donHangRepository,
                nguoiDungRepository, sachRepository, nguoiMua, maSach));

        ResponseEntity<String> response = guiDanhGia(nguoiLa, "Toi muon danh gia bang don cua nguoi khac");

        assertThat(response.getStatusCode().value())
                .as("don cua nguoi khac khong phai bang chung cua minh")
                .isEqualTo(403);
    }

    @Test
    void danh_gia_lan_hai_van_la_409_chu_khong_phai_403() {
        donDaTao.add(DonHangDaGiaoFixture.taoDonDaGiao(txManager, donHangRepository,
                nguoiDungRepository, sachRepository, nguoiMua, maSach));
        assertThat(guiDanhGia(nguoiMua, "Lan dau").getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> lanHai = guiDanhGia(nguoiMua, "Lan hai cua cung mot nguoi");

        assertThat(lanHai.getStatusCode().value())
                .as("da danh gia roi la xung dot trang thai (409), khong phai thieu quyen (403)")
                .isEqualTo(409);
    }

    // ---------------------------------------------------------------------

    private ResponseEntity<String> guiDanhGia(String tenDangNhap, String nhanXet) {
        HttpHeaders headers = bearer(tenDangNhap);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"maSach\":" + maSach + ",\"diemXepHang\":5,\"nhanXet\":\"" + nhanXet + "\"}";
        return rest.postForEntity("/api/danh-gia/them-danh-gia-v1",
                new HttpEntity<>(body, headers), String.class);
    }

    private HttpHeaders bearer(String tenDangNhap) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity("/tai-khoan/dang-nhap",
                new HttpEntity<>("{\"username\":\"" + tenDangNhap + "\",\"password\":\"" + MAT_KHAU + "\"}", headers),
                String.class);
        Matcher matcher = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")
                .matcher(response.getBody() == null ? "" : response.getBody());
        assertThat(matcher.find()).as("dang nhap %s thanh cong", tenDangNhap).isTrue();

        HttpHeaders bearerHeaders = new HttpHeaders();
        bearerHeaders.setBearerAuth(matcher.group());
        return bearerHeaders;
    }

    private int taoSach() {
        return new TransactionTemplate(txManager).execute(status -> {
            Sach mau = sachRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("can it nhat mot cuon sach seed"));
            Sach sach = new Sach();
            sach.setTenSach("Verified Purchase Book " + System.nanoTime());
            sach.setTenTacGia("Fixture");
            sach.setMoTa("Sach fixture cho VerifiedPurchaseIT");
            sach.setGiaNiemYet(mau.getGiaNiemYet());
            sach.setGiaBan(mau.getGiaBan());
            sach.setSoLuong(100);
            sach.setTrungBinhXepHang(0);
            sach.setSoLuotDanhGia(0);
            sach.setIsActive(1);
            return sachRepository.saveAndFlush(sach).getMaSach();
        });
    }

    private String taoNguoiDung(String tenDangNhap) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Quyen quyen = quyenRepository.findByTenQuyen("USER");
            assertThat(quyen).as("quyen USER da duoc seed").isNotNull();

            NguoiDung user = new NguoiDung();
            user.setHoDem("Verified");
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
