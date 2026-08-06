package com.example.book_be.portfolio;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.danhgia.repository.DanhGiaHuuIchRepository;
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
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binh chon "huu ich": bat/tat duoc, khong tu binh chon, khong trung, khong mo coi.
 *
 * <p>Khong co cot dem san nao. So luot tinh bang mot cau GROUP BY tren tap id cua trang,
 * nen khong sinh ra bat bien hai nguon su that phai canh — khac han
 * {@code trung_binh_xep_hang}, von da ton tai trong contract va thieu writer la bug that.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class HuuIchIT {

    private static final String MAT_KHAU = "HuuIch@12345";

    @Autowired TestRestTemplate rest;
    @Autowired DanhGiaService danhGiaService;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired QuyenRepository quyenRepository;
    @Autowired SuDanhGiaRepository suDanhGiaRepository;
    @Autowired DanhGiaHuuIchRepository danhGiaHuuIchRepository;
    @Autowired SachRepository sachRepository;
    @Autowired DonHangRepository donHangRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager txManager;

    private final List<String> nguoiDungDaTao = new ArrayList<>();
    private final List<Integer> donDaTao = new ArrayList<>();
    private String tacGia;
    private String nguoiDoc;
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
        tacGia = taoNguoiDung();
        nguoiDoc = taoNguoiDung();
        donDaTao.add(DonHangDaGiaoFixture.taoDonDaGiao(txManager, donHangRepository,
                nguoiDungRepository, sachRepository, tacGia, maSach));
        maDanhGia = danhGiaService.addReview("Danh gia de duoc binh chon", 5F,
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
    void binh_chon_roi_binh_chon_lai_la_go_binh_chon() {
        assertThat(binhChon(nguoiDoc).getBody()).contains("\"soLuotHuuIch\":1");
        assertThat(binhChon(nguoiDoc).getBody())
                .as("bam lan hai la go binh chon, nhu nut thich o moi noi khac")
                .contains("\"soLuotHuuIch\":0");
    }

    /** Chan o service chu khong o giao dien: an nut khong ngan mot request gui thang API. */
    @Test
    void khong_tu_binh_chon_duoc_danh_gia_cua_minh() {
        ResponseEntity<String> response = binhChon(tacGia);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(demBinhChon()).isZero();
    }

    @Test
    void khach_chua_dang_nhap_khong_binh_chon_duoc() {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/danh-gia/{id}/huu-ich", null, String.class, maDanhGia);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    /**
     * Hai request dong thoi cua cung mot nguoi. Rang buoc UNIQUE cua V11 la thu bao dam
     * tinh duy nhat — kiem tra o service chi de tra loi dung, khong phai de chan.
     */
    @Test
    void hai_request_dong_thoi_khong_tao_hai_dong() throws Exception {
        HttpHeaders headers = bearer(nguoiDoc);
        CountDownLatch batDau = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> { batDau.await(); return goiBinhChon(headers); });
            var b = pool.submit(() -> { batDau.await(); return goiBinhChon(headers); });
            batDau.countDown();
            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(demBinhChon())
                .as("khong bao gio duoc co hai dong cho cung mot cap (danh gia, nguoi dung)")
                .isLessThanOrEqualTo(1);
    }

    /** Cascade cua V11: xoa danh gia phai keo theo binh chon, neu khong so luot dem ca dong mo coi. */
    @Test
    void xoa_danh_gia_don_luon_binh_chon_kem_theo() {
        binhChon(nguoiDoc);
        assertThat(demBinhChon()).isEqualTo(1);

        danhGiaService.deleteReview(maDanhGia, maNguoiDungCua(tacGia), true);

        assertThat(demBinhChon())
                .as("binh chon mo coi se lam so luot bao cao sai vinh vien")
                .isZero();
    }

    @Test
    void so_luot_va_co_da_binh_chon_hien_trong_trang_doc() {
        binhChon(nguoiDoc);

        String cuaNguoiDaBinhChon = rest.exchange("/api/danh-gia?maSach={maSach}&size=50",
                HttpMethod.GET, new HttpEntity<>(bearer(nguoiDoc)), String.class, maSach).getBody();
        assertThat(cuaNguoiDaBinhChon)
                .contains("\"soLuotHuuIch\":1")
                .contains("\"toiDaBinhChon\":true");

        String cuaKhach = rest.getForObject("/api/danh-gia?maSach={maSach}&size=50", String.class, maSach);
        assertThat(cuaKhach)
                .as("khach an danh van thay so luot, nhung khong so huu luot nao")
                .contains("\"soLuotHuuIch\":1")
                .contains("\"toiDaBinhChon\":false");
    }

    /** Sap xep theo luot huu ich tinh bang JOIN, khong doc cot dem san. */
    @Test
    void sap_xep_theo_huu_ich_dua_bai_duoc_binh_chon_len_dau() {
        String nguoiKhac = taoNguoiDung();
        donDaTao.add(DonHangDaGiaoFixture.taoDonDaGiao(txManager, donHangRepository,
                nguoiDungRepository, sachRepository, nguoiKhac, maSach));
        long maDanhGiaKhac = danhGiaService.addReview("Danh gia khong ai binh chon", 4F,
                maNguoiDungCua(nguoiKhac), (long) maSach).getMaDanhGia();
        binhChon(nguoiDoc);

        String body = rest.getForObject(
                "/api/danh-gia?maSach={maSach}&sort=huu-ich&size=50", String.class, maSach);

        assertThat(body.indexOf("\"maDanhGia\":" + maDanhGia))
                .as("bai co luot binh chon phai dung truoc bai khong co")
                .isLessThan(body.indexOf("\"maDanhGia\":" + maDanhGiaKhac));
    }

    // ---------------------------------------------------------------------

    private ResponseEntity<String> binhChon(String tenDangNhap) {
        return goiBinhChon(bearer(tenDangNhap));
    }

    private ResponseEntity<String> goiBinhChon(HttpHeaders headers) {
        return rest.postForEntity("/api/danh-gia/{id}/huu-ich",
                new HttpEntity<>(headers), String.class, maDanhGia);
    }

    private long demBinhChon() {
        return danhGiaHuuIchRepository.demTheoDanhGia(List.of(maDanhGia)).stream()
                .findFirst().map(dong -> ((Number) dong[1]).longValue()).orElse(0L);
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
            sach.setTenSach("Huu Ich Fixture Book " + System.nanoTime());
            sach.setTenTacGia("Fixture");
            sach.setMoTa("Sach fixture cho HuuIchIT");
            sach.setGiaNiemYet(mau.getGiaNiemYet());
            sach.setGiaBan(mau.getGiaBan());
            sach.setSoLuong(100);
            sach.setTrungBinhXepHang(0);
            sach.setSoLuotDanhGia(0);
            sach.setIsActive(1);
            return sachRepository.saveAndFlush(sach).getMaSach();
        });
    }

    private String taoNguoiDung() {
        String tenDangNhap = "huuich-" + System.nanoTime();
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Quyen quyen = quyenRepository.findByTenQuyen("USER");
            NguoiDung user = new NguoiDung();
            user.setHoDem("Huu");
            user.setTen("Ich");
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
