package com.example.book_be.portfolio;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.danhgia.repository.SuDanhGiaRepository;
import com.example.book_be.danhgia.service.DanhGiaService;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Duong doc cong khai khong duoc mang danh tinh cua nguoi danh gia ra ngoai.
 *
 * <p>Bo {@code maNguoiDung} khong phai chi de gon response. Tu khi chi nguoi da nhan hang
 * moi danh gia duoc, MOI danh gia la bang chung cua mot don hang da giao. Mot dinh danh
 * on dinh di kem cho phep khach an danh quet ca catalog — viec phan trang lam re di —
 * gom theo id, va dung lai lich su mua hang cua tung nguoi. Che ten ma van tra id thi
 * viec che chi la trang tri.
 *
 * <p>Test nay CHI ap cho DTO cong khai. Man kiem duyet phai nhin thay danh tinh that:
 * xem {@link #duong_quan_tri_van_giu_danh_tinh_that()}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class DanhGiaPiiIT {

    private static final String MAT_KHAU = "DanhGiaPii@123";
    private static final String HO_DEM = "Nguyễn Văn";
    private static final String TEN = "An";

    @Autowired TestRestTemplate rest;
    @Autowired DanhGiaService danhGiaService;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired QuyenRepository quyenRepository;
    @Autowired SuDanhGiaRepository suDanhGiaRepository;
    @Autowired SachRepository sachRepository;
    @Autowired DonHangRepository donHangRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager txManager;

    private String tacGia;
    private String nguoiKhac;
    private String quanTri;
    private String email;
    private int maSach;
    private Integer maDonHang;

    @BeforeEach
    void provisionFixtures() {
        long runId = System.nanoTime();
        tacGia = taoNguoiDung("pii-author-" + runId, "USER");
        nguoiKhac = taoNguoiDung("pii-other-" + runId, "USER");
        quanTri = taoNguoiDung("pii-admin-" + runId, "ADMIN");
        email = tacGia + "@example.test";
        maSach = taoSach();
        maDonHang = DonHangDaGiaoFixture.taoDonDaGiao(txManager, donHangRepository,
                nguoiDungRepository, sachRepository, tacGia, maSach);
        danhGiaService.addReview("Danh gia de kiem tra PII", 5F, maNguoiDungCua(tacGia), (long) maSach);
    }

    @AfterEach
    void cleanupFixtures() {
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                suDanhGiaRepository.findAll().stream()
                        .filter(d -> d.getSach() != null && d.getSach().getMaSach() == maSach)
                        .forEach(suDanhGiaRepository::delete));
        DonHangDaGiaoFixture.xoaDon(txManager, donHangRepository, maDonHang);
        maDonHang = null;
        xoaNguoiDung(tacGia);
        xoaNguoiDung(nguoiKhac);
        xoaNguoiDung(quanTri);
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                sachRepository.findById((long) maSach).ifPresent(sachRepository::delete));
    }

    @Test
    void duong_cong_khai_khong_mang_dinh_danh_on_dinh_hay_thong_tin_lien_he() {
        String body = rest.getForObject("/api/danh-gia?maSach={maSach}&size=50", String.class, maSach);

        assertThat(body)
                .as("maNguoiDung la dinh danh on dinh xuyen sach — no ghep duoc toan bo lich su mua hang")
                .doesNotContain("maNguoiDung")
                .doesNotContain("nguoiDung")
                .doesNotContain(email)
                .doesNotContain(tacGia)
                .doesNotContain("matKhau")
                .doesNotContain("soDienThoai");
    }

    @Test
    void ten_hien_thi_da_duoc_che_o_backend() {
        String body = rest.getForObject("/api/danh-gia?maSach={maSach}&size=50", String.class, maSach);

        assertThat(body)
                .as("che phai lam o backend; che o frontend thi ten day du van nam trong response")
                .contains("\"tenHienThi\":\"Nguyễn V. A.\"")
                .doesNotContain(HO_DEM + " " + TEN);
    }

    /**
     * Doi trong cua test tren. Neu ai do sau nay siet PII qua tay va bo danh tinh khoi ca
     * duong quan tri, man kiem duyet se mat kha nang nhin thay minh dang go bai cua ai —
     * trong khi do chinh la viec cua no.
     */
    @Test
    void duong_quan_tri_van_giu_danh_tinh_that() {
        ResponseEntity<String> response = rest.exchange("/api/admin/danh-gia/findAll?page=0",
                HttpMethod.GET, new HttpEntity<>(bearer(quanTri)), String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .contains("maNguoiDung")
                .contains("tenNguoiDung");
    }

    @Test
    void la_cua_toi_dung_cho_chu_so_huu_va_sai_cho_moi_nguoi_khac() {
        assertThat(docTrangVoi(bearer(tacGia)))
                .as("chu so huu phai nhan dien duoc bai cua minh")
                .contains("\"laCuaToi\":true");
        assertThat(docTrangVoi(bearer(nguoiKhac)))
                .contains("\"laCuaToi\":false");
        assertThat(rest.getForObject("/api/danh-gia?maSach={maSach}&size=50", String.class, maSach))
                .as("khach an danh khong so huu bai nao")
                .contains("\"laCuaToi\":false");
    }

    // ---------------------------------------------------------------------

    private String docTrangVoi(HttpHeaders headers) {
        return rest.exchange("/api/danh-gia?maSach={maSach}&size=50", HttpMethod.GET,
                new HttpEntity<>(headers), String.class, maSach).getBody();
    }

    private Long maNguoiDungCua(String tenDangNhap) {
        return new TransactionTemplate(txManager).execute(status ->
                (long) nguoiDungRepository.findByTenDangNhap(tenDangNhap).getMaNguoiDung());
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
            sach.setTenSach("PII Fixture Book " + System.nanoTime());
            sach.setTenTacGia("Fixture");
            sach.setMoTa("Sach fixture cho DanhGiaPiiIT");
            sach.setGiaNiemYet(mau.getGiaNiemYet());
            sach.setGiaBan(mau.getGiaBan());
            sach.setSoLuong(100);
            sach.setTrungBinhXepHang(0);
            sach.setSoLuotDanhGia(0);
            sach.setIsActive(1);
            return sachRepository.saveAndFlush(sach).getMaSach();
        });
    }

    private String taoNguoiDung(String tenDangNhap, String tenQuyen) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Quyen quyen = quyenRepository.findByTenQuyen(tenQuyen);
            assertThat(quyen).as("quyen %s da duoc seed", tenQuyen).isNotNull();

            NguoiDung user = new NguoiDung();
            user.setHoDem(HO_DEM);
            user.setTen(TEN);
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
