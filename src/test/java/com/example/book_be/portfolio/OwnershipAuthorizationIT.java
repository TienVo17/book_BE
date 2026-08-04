package com.example.book_be.portfolio;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.donhang.domain.DonHang;
import com.example.book_be.donhang.repository.DonHangRepository;
import com.example.book_be.giohang.repository.GioHangRepository;
import com.example.book_be.nguoidung.domain.DiaChiGiaoHang;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.domain.Quyen;
import com.example.book_be.nguoidung.repository.DiaChiGiaoHangRepository;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.repository.QuyenRepository;
import com.example.book_be.sach.repository.SachRepository;
import com.example.book_be.yeuthich.domain.SachYeuThich;
import com.example.book_be.yeuthich.repository.SachYeuThichRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User A / User B cross-ownership matrix: dia chi, don hang, gio hang, yeu thich. B khong duoc
 * xem/sua/xoa tai nguyen cua A; loi phai la 403 tuong minh (khong phai 400 chung chung).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class OwnershipAuthorizationIT {

    private static final String FIXTURE_PREFIX = "ownership-";
    private static final String FIXTURE_PASSWORD = "Ownership@123";
    private static final int MA_SACH = 1;

    @Autowired TestRestTemplate rest;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired QuyenRepository quyenRepository;
    @Autowired DonHangRepository donHangRepository;
    @Autowired DiaChiGiaoHangRepository diaChiGiaoHangRepository;
    @Autowired GioHangRepository gioHangRepository;
    @Autowired SachYeuThichRepository sachYeuThichRepository;
    @Autowired SachRepository sachRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager txManager;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final List<Long> orderFixtures = new ArrayList<>();
    private final List<Long> userFixtures = new ArrayList<>();
    private final List<Long> addressFixtures = new ArrayList<>();
    private String fixtureRunId;
    private NguoiDung userA;
    private NguoiDung userB;
    private String jwtA;
    private String jwtB;

    @BeforeEach
    void provisionFixtures() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory(httpClient));
        fixtureRunId = FIXTURE_PREFIX + System.nanoTime();
        userA = taoNguoiDung("a");
        userB = taoNguoiDung("b");
        jwtA = login(userA.getTenDangNhap());
        jwtB = login(userB.getTenDangNhap());
    }

    @AfterEach
    void cleanupFixtures() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            gioHangRepository.deleteGioHangByMaNguoiDung(userA.getMaNguoiDung());
            gioHangRepository.deleteGioHangByMaNguoiDung(userB.getMaNguoiDung());
            for (SachYeuThich yeuThich : sachYeuThichRepository.findByNguoiDung_MaNguoiDung(userA.getMaNguoiDung())) {
                sachYeuThichRepository.deleteById((long) yeuThich.getMaSachYeuThich());
            }
            for (SachYeuThich yeuThich : sachYeuThichRepository.findByNguoiDung_MaNguoiDung(userB.getMaNguoiDung())) {
                sachYeuThichRepository.deleteById((long) yeuThich.getMaSachYeuThich());
            }
            for (Long maDiaChi : addressFixtures) {
                if (diaChiGiaoHangRepository.existsById(maDiaChi)) {
                    diaChiGiaoHangRepository.deleteById(maDiaChi);
                }
            }
            for (Long maDonHang : orderFixtures) {
                if (donHangRepository.existsById(maDonHang)) {
                    donHangRepository.deleteById(maDonHang);
                }
            }
            for (Long maNguoiDung : userFixtures) {
                if (nguoiDungRepository.existsById(maNguoiDung)) {
                    nguoiDungRepository.deleteById(maNguoiDung);
                }
            }
        });
    }

    @Test
    void b_khong_the_sua_dia_chi_cua_a() {
        DiaChiGiaoHang diaChiA = taoDiaChi(userA);
        HttpHeaders headers = bearerJson(jwtB);
        String payload = "{\"hoTen\":\"Chiem doat\",\"soDienThoai\":\"0911111111\","
                + "\"diaChiDayDu\":\"Dia chi bi chiem doat\",\"macDinh\":false}";

        ResponseEntity<String> response = rest.exchange(
                "/api/dia-chi/{id}", HttpMethod.PUT, new HttpEntity<>(payload, headers), String.class,
                diaChiA.getMaDiaChi());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        DiaChiGiaoHang saved = diaChiGiaoHangRepository.findById((long) diaChiA.getMaDiaChi()).orElseThrow();
        assertThat(saved.getHoTen()).isNotEqualTo("Chiem doat");
    }

    @Test
    void b_khong_the_xoa_dia_chi_cua_a() {
        DiaChiGiaoHang diaChiA = taoDiaChi(userA);
        HttpHeaders headers = bearerHeaders(jwtB);

        ResponseEntity<String> response = rest.exchange(
                "/api/dia-chi/{id}", HttpMethod.DELETE, new HttpEntity<>(headers), String.class,
                diaChiA.getMaDiaChi());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(diaChiGiaoHangRepository.existsById((long) diaChiA.getMaDiaChi())).isTrue();
    }

    @Test
    void b_khong_the_xem_don_hang_cua_a() {
        DonHang donA = taoDonHang(userA);
        HttpHeaders headers = bearerHeaders(jwtB);

        ResponseEntity<String> response = rest.exchange(
                "/api/don-hang/{id}", HttpMethod.GET, new HttpEntity<>(headers), String.class, donA.getMaDonHang());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void b_khong_the_huy_don_hang_cua_a() {
        DonHang donA = taoDonHang(userA);
        HttpHeaders headers = bearerHeaders(jwtB);

        ResponseEntity<String> response = rest.exchange(
                "/api/don-hang/huy/{id}", HttpMethod.POST, new HttpEntity<>(headers), String.class,
                donA.getMaDonHang());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        DonHang sau = donHangRepository.findById((long) donA.getMaDonHang()).orElseThrow();
        assertThat(sau.getTrangThaiGiaoHang()).isZero();
    }

    @Test
    void gio_hang_cua_b_khong_bao_gio_chua_du_lieu_cua_a() {
        themVaoGioHang(userA, jwtA);

        HttpHeaders headers = bearerHeaders(jwtB);
        ResponseEntity<String> response = rest.exchange(
                "/api/gio-hang", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().doesNotContain("\"maSach\":" + MA_SACH);
        assertThat(gioHangRepository.findByMaNguoiDung(userA.getMaNguoiDung())).isNotEmpty();
    }

    @Test
    void b_xoa_gio_hang_khong_anh_huong_gio_hang_cua_a() {
        themVaoGioHang(userA, jwtA);

        HttpHeaders headers = bearerHeaders(jwtB);
        ResponseEntity<String> response = rest.exchange(
                "/api/gio-hang/items/{maSach}", HttpMethod.DELETE, new HttpEntity<>(headers), String.class, MA_SACH);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(gioHangRepository.findByMaNguoiDung(userA.getMaNguoiDung()))
                .as("gio hang cua A khong bi xoa boi request cua B")
                .isNotEmpty();
    }

    @Test
    void b_khong_the_xoa_yeu_thich_cua_a() {
        themVaoYeuThich(userA, jwtA);

        HttpHeaders headers = bearerHeaders(jwtB);
        ResponseEntity<String> response = rest.exchange(
                "/api/yeu-thich/{maSach}", HttpMethod.DELETE, new HttpEntity<>(headers), String.class, MA_SACH);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(sachYeuThichRepository.existsByNguoiDung_MaNguoiDungAndSach_MaSach(userA.getMaNguoiDung(), MA_SACH))
                .as("yeu thich cua A van con sau khi B thu xoa")
                .isTrue();
    }

    @Test
    void yeu_thich_cua_b_khong_bao_gio_chua_du_lieu_cua_a() {
        themVaoYeuThich(userA, jwtA);

        HttpHeaders headers = bearerHeaders(jwtB);
        ResponseEntity<String> response = rest.exchange(
                "/api/yeu-thich", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().doesNotContain("\"maSach\":" + MA_SACH);
    }

    private void themVaoGioHang(NguoiDung user, String jwt) {
        HttpHeaders headers = bearerJson(jwt);
        String payload = "{\"maSach\":" + MA_SACH + ",\"soLuong\":1}";
        ResponseEntity<String> response = rest.postForEntity(
                "/api/gio-hang/items", new HttpEntity<>(payload, headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void themVaoYeuThich(NguoiDung user, String jwt) {
        HttpHeaders headers = bearerHeaders(jwt);
        ResponseEntity<String> response = rest.postForEntity(
                "/api/yeu-thich/{maSach}", new HttpEntity<>(headers), String.class, MA_SACH);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private HttpHeaders bearerHeaders(String jwt) {
        assertThat(jwt).isNotBlank();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        return headers;
    }

    private HttpHeaders bearerJson(String jwt) {
        HttpHeaders headers = bearerHeaders(jwt);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String login(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity(
                "/tai-khoan/dang-nhap",
                new HttpEntity<>("{\"username\":\"" + username + "\",\"password\":\"" + FIXTURE_PASSWORD + "\"}", headers),
                String.class);
        String content = response.getBody() == null ? "" : response.getBody();
        Matcher matcher = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+").matcher(content);
        String jwt = matcher.find() ? matcher.group() : "";
        assertThat(jwt).as("login succeeded for " + username).isNotBlank();
        return jwt;
    }

    /**
     * NguoiDung.danhSachQuyen cascades PERSIST, so a Quyen loaded outside this transaction
     * would arrive detached and fail the cascade. Load the role and save the user inside one
     * transaction so the role stays managed.
     */
    private NguoiDung taoNguoiDung(String suffix) {
        NguoiDung saved = new TransactionTemplate(txManager).execute(status -> {
            Quyen userRole = quyenRepository.findByTenQuyen("USER");
            assertThat(userRole).as("seed USER role exists").isNotNull();

            NguoiDung user = new NguoiDung();
            user.setHoDem("Ownership");
            user.setTen("Fixture");
            user.setTenDangNhap(fixtureRunId + "-" + suffix);
            user.setMatKhau(passwordEncoder.encode(FIXTURE_PASSWORD));
            user.setGioiTinh('X');
            user.setEmail(fixtureRunId + "-" + suffix + "@example.test");
            user.setSoDienThoai("0900000000");
            user.setDiaChiMuaHang("Ownership fixture address");
            user.setDiaChiGiaoHang("Ownership fixture delivery address");
            user.setDaKichHoat(true);
            user.setDanhSachQuyen(List.of(userRole));

            return nguoiDungRepository.saveAndFlush(user);
        });

        assertThat(saved).isNotNull();
        userFixtures.add((long) saved.getMaNguoiDung());
        return saved;
    }

    private DiaChiGiaoHang taoDiaChi(NguoiDung owner) {
        DiaChiGiaoHang saved = new TransactionTemplate(txManager).execute(status -> {
            NguoiDung managedOwner = nguoiDungRepository.findById((long) owner.getMaNguoiDung())
                    .orElseThrow(() -> new IllegalStateException("fixture user missing"));

            DiaChiGiaoHang diaChi = new DiaChiGiaoHang();
            diaChi.setNguoiDung(managedOwner);
            diaChi.setHoTen("Owner Address");
            diaChi.setSoDienThoai("0900000000");
            diaChi.setDiaChiDayDu("Owner address fixture");
            diaChi.setMacDinh(false);

            return diaChiGiaoHangRepository.saveAndFlush(diaChi);
        });

        assertThat(saved).isNotNull();
        addressFixtures.add((long) saved.getMaDiaChi());
        return saved;
    }

    /**
     * DonHang.nguoiDung cascades PERSIST, so the owner must be re-read inside the same
     * transaction that saves the order; passing the detached fixture instance would fail.
     */
    private DonHang taoDonHang(NguoiDung owner) {
        DonHang saved = new TransactionTemplate(txManager).execute(status -> {
            NguoiDung managedOwner = nguoiDungRepository.findById((long) owner.getMaNguoiDung())
                    .orElseThrow(() -> new IllegalStateException("fixture user missing"));

            DonHang order = new DonHang();
            order.setNgayTao(new Date());
            order.setNguoiDung(managedOwner);
            order.setHoTen("Ownership fixture order");
            order.setSoDienThoai("0900000000");
            order.setTongTienSanPham(0D);
            order.setChiPhiGiaoHang(0D);
            order.setChiPhiThanhToan(0D);
            order.setTongTien(0D);
            order.setTrangThaiThanhToan(0);
            order.setTrangThaiGiaoHang(0);

            return donHangRepository.saveAndFlush(order);
        });

        assertThat(saved).isNotNull();
        orderFixtures.add((long) saved.getMaDonHang());
        return saved;
    }
}
