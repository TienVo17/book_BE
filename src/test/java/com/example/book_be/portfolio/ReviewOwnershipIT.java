package com.example.book_be.portfolio;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.danhgia.repository.SuDanhGiaRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.http.HttpClient;
import java.sql.Timestamp;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sua/xoa danh gia chi thuoc ve chu so huu (hoac ADMIN kiem duyet). Mot nguoi dung khac
 * khong duoc sua hay xoa danh gia cua nguoi khac, va khach an danh khong duoc dung cac route nay.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class ReviewOwnershipIT {

    private static final String MAT_KHAU = "ReviewOwnership@123";

    @Autowired TestRestTemplate rest;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired QuyenRepository quyenRepository;
    @Autowired SuDanhGiaRepository suDanhGiaRepository;
    @Autowired SachRepository sachRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager txManager;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private String chuSoHuu;
    private String nguoiKhac;
    private String quanTri;
    private Long maDanhGia;

    @BeforeEach
    void provisionFixtures() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory(httpClient));
        long runId = System.nanoTime();
        chuSoHuu = taoNguoiDung("review-owner-" + runId, "USER");
        nguoiKhac = taoNguoiDung("review-other-" + runId, "USER");
        quanTri = taoNguoiDung("review-admin-" + runId, "ADMIN");
        maDanhGia = taoDanhGia(chuSoHuu);
    }

    @AfterEach
    void cleanupFixtures() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            if (maDanhGia != null) {
                suDanhGiaRepository.findById(maDanhGia).ifPresent(suDanhGiaRepository::delete);
            }
        });
        xoaNguoiDung(chuSoHuu);
        xoaNguoiDung(nguoiKhac);
        xoaNguoiDung(quanTri);
    }

    @Test
    void nguoi_dung_khac_khong_sua_duoc_danh_gia_cua_nguoi_khac() {
        ResponseEntity<String> response = suaDanhGia(bearer(nguoiKhac), "Bi chiem quyen", 1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(noiDungDanhGia()).isEqualTo("Danh gia goc cua chu so huu");
    }

    @Test
    void nguoi_dung_khac_khong_xoa_duoc_danh_gia_cua_nguoi_khac() {
        ResponseEntity<String> response = xoaDanhGia(bearer(nguoiKhac));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(suDanhGiaRepository.findById(maDanhGia)).isPresent();
    }

    @Test
    void khach_an_danh_bi_tu_choi() {
        assertThat(suaDanhGia(new HttpHeaders(), "An danh", 1).getStatusCode().value()).isIn(401, 403);
        assertThat(xoaDanhGia(new HttpHeaders()).getStatusCode().value()).isIn(401, 403);
        assertThat(suDanhGiaRepository.findById(maDanhGia)).isPresent();
    }

    @Test
    void chu_so_huu_van_sua_duoc_danh_gia_cua_minh() {
        ResponseEntity<String> response = suaDanhGia(bearer(chuSoHuu), "Da cap nhat", 4);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(noiDungDanhGia()).isEqualTo("Da cap nhat");
    }

    @Test
    void admin_kiem_duyet_xoa_duoc_danh_gia() {
        ResponseEntity<String> response = xoaDanhGia(bearer(quanTri));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(suDanhGiaRepository.findById(maDanhGia)).isEmpty();
    }

    private ResponseEntity<String> suaDanhGia(HttpHeaders headers, String nhanXet, int diem) {
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.putAll(headers);
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        String payload = "{\"nhanXet\":\"" + nhanXet + "\",\"diemXepHang\":" + diem + "}";
        return rest.postForEntity("/api/danh-gia/sua-danh-gia/{id}",
                new HttpEntity<>(payload, requestHeaders), String.class, maDanhGia);
    }

    private ResponseEntity<String> xoaDanhGia(HttpHeaders headers) {
        return rest.postForEntity("/api/danh-gia/xoa-danh-gia/{id}",
                new HttpEntity<>(headers), String.class, maDanhGia);
    }

    private String noiDungDanhGia() {
        return suDanhGiaRepository.findById(maDanhGia).orElseThrow().getNhanXet();
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

    private Long taoDanhGia(String tenDangNhap) {
        return new TransactionTemplate(txManager).execute(status -> {
            NguoiDung nguoiDung = nguoiDungRepository.findByTenDangNhap(tenDangNhap);
            Sach sach = sachRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("can it nhat mot cuon sach seed"));

            SuDanhGia danhGia = new SuDanhGia();
            danhGia.setNhanXet("Danh gia goc cua chu so huu");
            danhGia.setDiemXepHang(5F);
            danhGia.setTimestamp(new Timestamp(System.currentTimeMillis()));
            danhGia.datTrangThai(com.example.book_be.danhgia.domain.TrangThaiDanhGia.HIEN_THI);
            danhGia.setNguoiDung(nguoiDung);
            danhGia.setSach(sach);
            return suDanhGiaRepository.saveAndFlush(danhGia).getMaDanhGia();
        });
    }

    private String taoNguoiDung(String tenDangNhap, String tenQuyen) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Quyen quyen = quyenRepository.findByTenQuyen(tenQuyen);
            assertThat(quyen).as("quyen %s da duoc seed", tenQuyen).isNotNull();

            NguoiDung user = new NguoiDung();
            user.setHoDem("Review");
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
