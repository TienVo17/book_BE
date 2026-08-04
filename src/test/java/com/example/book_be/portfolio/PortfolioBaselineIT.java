package com.example.book_be.portfolio;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.donhang.domain.DonHang;
import com.example.book_be.donhang.repository.DonHangRepository;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.domain.Quyen;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.repository.QuyenRepository;
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

/** Stable public catalog, checkout-boundary, and order-ownership HTTP contracts on real MySQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class PortfolioBaselineIT {

    private static final String FIXTURE_PREFIX = "portfolio-baseline-";
    private static final String FIXTURE_PASSWORD = "PortfolioBaseline@123";

    @Autowired TestRestTemplate rest;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired QuyenRepository quyenRepository;
    @Autowired DonHangRepository donHangRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager txManager;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final List<Long> orderFixtures = new ArrayList<>();
    private final List<Long> userFixtures = new ArrayList<>();
    private String fixtureRunId;
    private NguoiDung owner;
    private NguoiDung otherUser;

    @BeforeEach
    void provisionFixtures() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory(httpClient));
        fixtureRunId = FIXTURE_PREFIX + System.nanoTime();
        owner = taoNguoiDung("owner");
        otherUser = taoNguoiDung("other");
    }

    @AfterEach
    void cleanupFixtures() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
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
    void catalog_cong_khai_tra_200_voi_cac_truong_san_pham_on_dinh() {
        ResponseEntity<String> response = rest.getForEntity("/api/sach?page=0", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .isNotBlank()
                .contains("\"maSach\"")
                .contains("\"tenSach\"")
                .contains("\"giaBan\"");
    }

    @Test
    void checkout_an_danh_bi_tu_choi() {
        ResponseEntity<String> response = postCheckout("{}", new HttpHeaders());

        assertThat(response.getStatusCode().value()).isIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
    }

    @Test
    void checkout_da_xac_thuc_tu_choi_thieu_san_pham_dia_chi_hoac_thanh_toan() {
        HttpHeaders headers = bearerHeaders(login(otherUser.getTenDangNhap(), FIXTURE_PASSWORD));

        for (String payload : List.of(
                "{\"maDiaChiGiaoHang\":1,\"phuongThucThanhToan\":\"COD\"}",
                "{\"items\":[{\"maSach\":1,\"soLuong\":1}],\"phuongThucThanhToan\":\"COD\"}",
                "{\"items\":[{\"maSach\":1,\"soLuong\":1}],\"maDiaChiGiaoHang\":1}")) {
            ResponseEntity<String> response = postCheckout(payload, headers);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void nguoi_dung_khong_the_xem_chi_tiet_don_hang_cua_nguoi_khac() {
        DonHang order = taoDonHang(owner);
        HttpHeaders headers = bearerHeaders(login(otherUser.getTenDangNhap(), FIXTURE_PASSWORD));

        ResponseEntity<String> response = rest.exchange(
                "/api/don-hang/{id}", HttpMethod.GET, new HttpEntity<>(headers), String.class, order.getMaDonHang());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<String> postCheckout(String payload, HttpHeaders headers) {
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.putAll(headers);
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity("/api/don-hang/them", new HttpEntity<>(payload, requestHeaders), String.class);
    }

    private HttpHeaders bearerHeaders(String jwt) {
        assertThat(jwt).isNotBlank();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        return headers;
    }

    private String login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity(
                "/tai-khoan/dang-nhap",
                new HttpEntity<>("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}", headers),
                String.class);
        String content = response.getBody() == null ? "" : response.getBody();
        Matcher matcher = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+").matcher(content);
        return matcher.find() ? matcher.group() : "";
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
            user.setHoDem("Portfolio");
            user.setTen("Baseline");
            user.setTenDangNhap(fixtureRunId + "-" + suffix);
            user.setMatKhau(passwordEncoder.encode(FIXTURE_PASSWORD));
            user.setGioiTinh('X');
            user.setEmail(fixtureRunId + "-" + suffix + "@example.test");
            user.setSoDienThoai("0900000000");
            user.setDiaChiMuaHang("Portfolio fixture address");
            user.setDiaChiGiaoHang("Portfolio fixture delivery address");
            user.setDaKichHoat(true);
            user.setDanhSachQuyen(List.of(userRole));

            return nguoiDungRepository.saveAndFlush(user);
        });

        assertThat(saved).isNotNull();
        userFixtures.add((long) saved.getMaNguoiDung());
        return saved;
    }

    /**
     * DonHang.nguoiDung cascades PERSIST, so the owner must be re-read inside the same
     * transaction that saves the order; passing the detached fixture instance would fail.
     */
    private DonHang taoDonHang(NguoiDung orderOwner) {
        DonHang saved = new TransactionTemplate(txManager).execute(status -> {
            NguoiDung managedOwner = nguoiDungRepository.findById((long) orderOwner.getMaNguoiDung())
                    .orElseThrow(() -> new IllegalStateException("fixture user missing"));

            DonHang order = new DonHang();
            order.setNgayTao(new Date());
            order.setNguoiDung(managedOwner);
            order.setHoTen("Portfolio fixture");
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
