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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shop tra loi cong khai duoi mot danh gia.
 *
 * <p>Phan hoi luu thang tren dong {@code danhgia} chu khong phai mot bang rieng, nen "moi
 * danh gia toi da mot phan hoi" la dieu cau truc khong cho phep vi pham — khong phai mot
 * quy tac phai canh bang code.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class PhanHoiShopIT {

    private static final String MAT_KHAU = "PhanHoiShop@123";

    @Autowired TestRestTemplate rest;
    @Autowired DanhGiaService danhGiaService;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired QuyenRepository quyenRepository;
    @Autowired SuDanhGiaRepository suDanhGiaRepository;
    @Autowired SachRepository sachRepository;
    @Autowired DonHangRepository donHangRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager txManager;

    private final List<String> nguoiDungDaTao = new ArrayList<>();
    private final List<Integer> donDaTao = new ArrayList<>();
    private String tacGia;
    private String quanTri;
    private int maSach;
    private long maDanhGia;

    @BeforeEach
    void provisionFixtures() {
        // HttpURLConnection mac dinh khong gui lai duoc mot POST khong body sau khi
        // nhan 401 ("cannot retry due to server authentication, in streaming mode").
        // Day la gioi han cua client trong test, khong phai hanh vi cua endpoint.
        rest.getRestTemplate().setRequestFactory(
                new JdkClientHttpRequestFactory(HttpClient.newHttpClient()));
        maSach = taoSach();
        tacGia = taoNguoiDung("USER");
        quanTri = taoNguoiDung("ADMIN");
        donDaTao.add(DonHangDaGiaoFixture.taoDonDaGiao(txManager, donHangRepository,
                nguoiDungRepository, sachRepository, tacGia, maSach));
        maDanhGia = danhGiaService.addReview("Sach giao hoi cham", 3F,
                maNguoiDungCua(tacGia), (long) maSach).getMaDanhGia();
    }

    @AfterEach
    void cleanupFixtures() {
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                suDanhGiaRepository.findAll().stream()
                        .filter(d -> d.getSach() != null && d.getSach().getMaSach() == maSach)
                        .forEach(suDanhGiaRepository::delete));
        donDaTao.forEach(ma -> DonHangDaGiaoFixture.xoaDon(txManager, donHangRepository, ma));
        donDaTao.clear();
        nguoiDungDaTao.forEach(this::xoaNguoiDung);
        nguoiDungDaTao.clear();
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                sachRepository.findById((long) maSach).ifPresent(sachRepository::delete));
    }

    @Test
    void admin_tra_loi_duoc_va_phan_hoi_hien_o_duong_cong_khai() {
        ResponseEntity<String> response = traLoi(quanTri, "Shop xin lỗi vì giao chậm.");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(rest.getForObject("/api/danh-gia?maSach={maSach}&size=50", String.class, maSach))
                .contains("Shop xin lỗi vì giao chậm.");
    }

    @Test
    void tra_loi_lan_hai_la_sua_chu_khong_tao_them() {
        traLoi(quanTri, "Phản hồi đầu tiên.");
        traLoi(quanTri, "Phản hồi đã sửa lại.");

        String body = rest.getForObject("/api/danh-gia?maSach={maSach}&size=50", String.class, maSach);
        assertThat(body)
                .contains("Phản hồi đã sửa lại.")
                .doesNotContain("Phản hồi đầu tiên.");
    }

    @Test
    void nguoi_thuong_khong_tra_loi_duoc() {
        ResponseEntity<String> response = traLoi(tacGia, "Tôi tự trả lời thay shop");

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void khach_chua_dang_nhap_khong_tra_loi_duoc() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity("/api/admin/danh-gia/{id}/phan-hoi",
                new HttpEntity<>("{\"noiDung\":\"khach\"}", headers), String.class, maDanhGia);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void noi_dung_rong_bi_tu_choi() {
        assertThat(traLoi(quanTri, "   ").getStatusCode().value()).isEqualTo(400);
    }

    /** Phan hoi cong khai khong duoc kem danh tinh cua nguoi tra loi. */
    @Test
    void phan_hoi_cong_khai_khong_lo_ai_da_tra_loi() {
        traLoi(quanTri, "Shop đã ghi nhận.");

        assertThat(rest.getForObject("/api/danh-gia?maSach={maSach}&size=50", String.class, maSach))
                .doesNotContain("phanHoiShopBoi")
                .doesNotContain(quanTri);
    }

    // ---------------------------------------------------------------------

    private ResponseEntity<String> traLoi(String tenDangNhap, String noiDung) {
        HttpHeaders headers = bearer(tenDangNhap);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity("/api/admin/danh-gia/{id}/phan-hoi",
                new HttpEntity<>("{\"noiDung\":\"" + noiDung + "\"}", headers), String.class, maDanhGia);
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
            sach.setTenSach("Phan Hoi Fixture Book " + System.nanoTime());
            sach.setTenTacGia("Fixture");
            sach.setMoTa("Sach fixture cho PhanHoiShopIT");
            sach.setGiaNiemYet(mau.getGiaNiemYet());
            sach.setGiaBan(mau.getGiaBan());
            sach.setSoLuong(100);
            sach.setTrungBinhXepHang(0);
            sach.setSoLuotDanhGia(0);
            sach.setIsActive(1);
            return sachRepository.saveAndFlush(sach).getMaSach();
        });
    }

    private String taoNguoiDung(String tenQuyen) {
        String tenDangNhap = "phanhoi-" + tenQuyen.toLowerCase() + "-" + System.nanoTime();
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Quyen quyen = quyenRepository.findByTenQuyen(tenQuyen);
            NguoiDung user = new NguoiDung();
            user.setHoDem("Phan");
            user.setTen("Hoi");
            user.setTenDangNhap(tenDangNhap);
            user.setMatKhau(passwordEncoder.encode(MAT_KHAU));
            user.setGioiTinh('X');
            user.setEmail(tenDangNhap + "@example.test");
            user.setDaKichHoat(true);
            user.setDanhSachQuyen(List.of(quyen));
            nguoiDungRepository.saveAndFlush(user);
        });
        nguoiDungDaTao.add(tenDangNhap);
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
