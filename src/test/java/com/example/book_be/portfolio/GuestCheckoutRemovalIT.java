package com.example.book_be.portfolio;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.donhang.domain.DonHang;
import com.example.book_be.donhang.repository.DonHangRepository;
import com.example.book_be.nguoidung.domain.DiaChiGiaoHang;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.domain.Quyen;
import com.example.book_be.nguoidung.repository.DiaChiGiaoHangRepository;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guest quick-order ("them-don-hang-moi") phai bien mat hoan toan: khong tao don, khong dung
 * kho/coupon, bat ke an danh hay da xac thuc. Checkout that ("them") van phai hoat dong binh
 * thuong va giu nguyen DTO thanh cong cho nguoi dung da dang nhap.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class GuestCheckoutRemovalIT {

    private static final String FIXTURE_PREFIX = "guest-checkout-removal-";
    private static final String FIXTURE_PASSWORD = "GuestRemoval@123";
    private static final int MA_SACH = 1;

    @Autowired TestRestTemplate rest;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired QuyenRepository quyenRepository;
    @Autowired DonHangRepository donHangRepository;
    @Autowired DiaChiGiaoHangRepository diaChiGiaoHangRepository;
    @Autowired SachRepository sachRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager txManager;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final List<Long> orderFixtures = new ArrayList<>();
    private final List<Long> userFixtures = new ArrayList<>();
    private final List<Long> addressFixtures = new ArrayList<>();
    private String fixtureRunId;
    private NguoiDung owner;
    private Integer tonKhoBanDau;

    @BeforeEach
    void provisionFixtures() {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory(httpClient));
        fixtureRunId = FIXTURE_PREFIX + System.nanoTime();
        owner = taoNguoiDung("owner");
    }

    @AfterEach
    void cleanupFixtures() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
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
        if (tonKhoBanDau != null) {
            int original = tonKhoBanDau;
            tonKhoBanDau = null;
            new TransactionTemplate(txManager).executeWithoutResult(status -> {
                Sach sach = sachRepository.findById((long) MA_SACH).orElseThrow();
                sach.setSoLuong(original);
                sachRepository.saveAndFlush(sach);
            });
        }
    }

    @Test
    void khach_an_danh_khong_the_tao_don_qua_guest_endpoint() {
        long soDonTruoc = donHangRepository.count();

        ResponseEntity<String> response = postGuestQuickOrder(new HttpHeaders());

        // Security tu choi truoc khi request cham toi DispatcherServlet (matcher/handler da bi xoa),
        // nen ma phan hoi la 403; endpoint khong con ton tai duoi bat ky hinh thuc nao (khong 200/2xx).
        assertThat(response.getStatusCode().is2xxSuccessful()).as("guest endpoint khong con hoat dong").isFalse();
        assertThat(response.getStatusCode().value()).isIn(
                HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value(), HttpStatus.NOT_FOUND.value());
        assertThat(donHangRepository.count()).as("khong co don nao duoc tao").isEqualTo(soDonTruoc);
    }

    @Test
    void nguoi_dung_da_xac_thuc_cung_khong_the_tao_don_qua_guest_endpoint() {
        HttpHeaders headers = bearerHeaders(login(owner.getTenDangNhap(), FIXTURE_PASSWORD));
        long soDonTruoc = donHangRepository.count();

        ResponseEntity<String> response = postGuestQuickOrder(headers);

        assertThat(response.getStatusCode().is2xxSuccessful()).as("guest endpoint khong con hoat dong").isFalse();
        assertThat(response.getStatusCode().value()).isIn(
                HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value(), HttpStatus.NOT_FOUND.value());
        assertThat(donHangRepository.count()).as("khong co don nao duoc tao").isEqualTo(soDonTruoc);
    }

    @Test
    void checkout_da_xac_thuc_van_thanh_cong_va_giu_nguyen_dto() {
        datTonKho(10);
        DiaChiGiaoHang diaChi = taoDiaChi(owner);
        HttpHeaders headers = bearerHeaders(login(owner.getTenDangNhap(), FIXTURE_PASSWORD));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "guest-removal-" + System.nanoTime());
        String payload = "{\"items\":[{\"maSach\":" + MA_SACH + ",\"soLuong\":1}],"
                + "\"maDiaChiGiaoHang\":" + diaChi.getMaDiaChi() + ",\"phuongThucThanhToan\":\"COD\"}";

        ResponseEntity<String> response = rest.postForEntity(
                "/api/don-hang/them", new HttpEntity<>(payload, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .isNotBlank()
                .contains("\"maDonHang\"")
                .contains("\"tongTien\"")
                .contains("\"phuongThucThanhToan\":\"COD\"");

        Matcher matcher = Pattern.compile("\"maDonHang\":(\\d+)").matcher(response.getBody());
        assertThat(matcher.find()).as("phan hoi chua maDonHang").isTrue();
        orderFixtures.add(Long.valueOf(matcher.group(1)));
    }

    private ResponseEntity<String> postGuestQuickOrder(HttpHeaders callerHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(callerHeaders);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String uri = UriComponentsBuilder.fromPath("/api/don-hang/them-don-hang-moi")
                .queryParam("hoTen", "Guest Removal")
                .queryParam("soDienThoai", "0900000000")
                .queryParam("diaChiNhanHang", "Guest fixture address")
                .toUriString();
        return rest.postForEntity(uri, new HttpEntity<>(headers), String.class);
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

    private void datTonKho(int giaTri) {
        if (tonKhoBanDau == null) {
            tonKhoBanDau = sachRepository.findById((long) MA_SACH).orElseThrow().getSoLuong();
        }
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Sach sach = sachRepository.findById((long) MA_SACH).orElseThrow();
            sach.setSoLuong(giaTri);
            sachRepository.saveAndFlush(sach);
        });
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
            user.setHoDem("Guest");
            user.setTen("Removal");
            user.setTenDangNhap(fixtureRunId + "-" + suffix);
            user.setMatKhau(passwordEncoder.encode(FIXTURE_PASSWORD));
            user.setGioiTinh('X');
            user.setEmail(fixtureRunId + "-" + suffix + "@example.test");
            user.setSoDienThoai("0900000000");
            user.setDiaChiMuaHang("Guest removal fixture address");
            user.setDiaChiGiaoHang("Guest removal fixture delivery address");
            user.setDaKichHoat(true);
            user.setDanhSachQuyen(List.of(userRole));

            return nguoiDungRepository.saveAndFlush(user);
        });

        assertThat(saved).isNotNull();
        userFixtures.add((long) saved.getMaNguoiDung());
        return saved;
    }

    /**
     * DiaChiGiaoHang.nguoiDung cascades PERSIST; re-read the owner inside this transaction so
     * the reference stays managed instead of detached.
     */
    private DiaChiGiaoHang taoDiaChi(NguoiDung owner) {
        DiaChiGiaoHang saved = new TransactionTemplate(txManager).execute(status -> {
            NguoiDung managedOwner = nguoiDungRepository.findById((long) owner.getMaNguoiDung())
                    .orElseThrow(() -> new IllegalStateException("fixture user missing"));

            DiaChiGiaoHang diaChi = new DiaChiGiaoHang();
            diaChi.setNguoiDung(managedOwner);
            diaChi.setHoTen("Guest Removal");
            diaChi.setSoDienThoai("0900000000");
            diaChi.setDiaChiDayDu("Guest removal checkout address");
            diaChi.setMacDinh(false);

            return diaChiGiaoHangRepository.saveAndFlush(diaChi);
        });

        assertThat(saved).isNotNull();
        addressFixtures.add((long) saved.getMaDiaChi());
        return saved;
    }
}
