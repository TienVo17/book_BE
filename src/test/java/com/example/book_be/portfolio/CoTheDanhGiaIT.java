package com.example.book_be.portfolio;

import com.example.book_be.TestcontainersConfig;
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
import org.springframework.http.HttpMethod;
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
 * {@code GET /api/danh-gia/co-the-danh-gia} phai noi ro VI SAO khong danh gia duoc.
 *
 * <p>Bon ly do dan toi bon hanh dong khac nhau ve phia nguoi dung. Mot thong bao chung
 * chung ("ban khong the danh gia") bat ho tu doan, va thuong doan sai.
 *
 * <p>Endpoint nay cung la cai bay kinh dien cua repo: {@code anyRequest().denyAll()} lam
 * moi route khong khai bao tuong minh trong SecurityConfiguration ship ra ma chet cam.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class CoTheDanhGiaIT {

    private static final String MAT_KHAU = "CoTheDanhGia@123";

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
    private String quanTri;
    private int maSach;

    @BeforeEach
    void provisionFixtures() {
        long runId = System.nanoTime();
        nguoiMua = taoNguoiDung("cothe-buyer-" + runId, "USER");
        quanTri = taoNguoiDung("cothe-admin-" + runId, "ADMIN");
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
        xoaNguoiDung(quanTri);
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                sachRepository.findById((long) maSach).ifPresent(sachRepository::delete));
    }

    @Test
    void chua_mua_thi_ly_do_la_chua_mua() {
        assertThat(goi(nguoiMua).getBody())
                .contains("\"coThe\":false")
                .contains("\"lyDo\":\"CHUA_MUA\"");
    }

    @Test
    void mua_roi_chua_nhan_thi_ly_do_la_chua_nhan_hang() {
        donDaTao.add(DonHangDaGiaoFixture.taoDonChuaGiao(txManager, donHangRepository,
                nguoiDungRepository, sachRepository, nguoiMua, maSach, TrangThaiGiaoHang.CHO_XU_LY));

        assertThat(goi(nguoiMua).getBody())
                .contains("\"lyDo\":\"CHUA_NHAN_HANG\"");
    }

    @Test
    void da_nhan_hang_thi_duoc_va_kem_ma_don_hang() {
        int maDonHang = DonHangDaGiaoFixture.taoDonDaGiao(txManager, donHangRepository,
                nguoiDungRepository, sachRepository, nguoiMua, maSach);
        donDaTao.add(maDonHang);

        assertThat(goi(nguoiMua).getBody())
                .contains("\"coThe\":true")
                .contains("\"maDonHang\":" + maDonHang);
    }

    @Test
    void da_danh_gia_thi_ly_do_la_da_danh_gia() {
        donDaTao.add(DonHangDaGiaoFixture.taoDonDaGiao(txManager, donHangRepository,
                nguoiDungRepository, sachRepository, nguoiMua, maSach));
        guiDanhGia(nguoiMua);

        assertThat(goi(nguoiMua).getBody())
                .contains("\"lyDo\":\"DA_DANH_GIA\"");
    }

    @Test
    void bi_an_thi_ly_do_la_da_bi_an() {
        donDaTao.add(DonHangDaGiaoFixture.taoDonDaGiao(txManager, donHangRepository,
                nguoiDungRepository, sachRepository, nguoiMua, maSach));
        guiDanhGia(nguoiMua);
        anDanhGiaMoiNhat();

        assertThat(goi(nguoiMua).getBody())
                .as("da bi an phai thang ly do DA_DANH_GIA — no la ket cuoi, khong phai tinh trang tam thoi")
                .contains("\"lyDo\":\"DA_BI_AN\"");
    }

    /**
     * Ket qua phu thuoc lich su mua hang cua chinh nguoi goi, nen khach an danh khong duoc
     * doc. 401 la cau tra loi dung o tang security; endpoint khong tra ve mot ly do thu nam.
     */
    @Test
    void khach_chua_dang_nhap_khong_goi_duoc() {
        ResponseEntity<String> response = rest.getForEntity(
                "/api/danh-gia/co-the-danh-gia?maSach={maSach}", String.class, maSach);

        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("endpoint nay khong duoc mo cong khai")
                .isFalse();
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    // ---------------------------------------------------------------------

    private ResponseEntity<String> goi(String tenDangNhap) {
        ResponseEntity<String> response = rest.exchange(
                "/api/danh-gia/co-the-danh-gia?maSach={maSach}", HttpMethod.GET,
                new HttpEntity<>(bearer(tenDangNhap)), String.class, maSach);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("endpoint phai co rule security, neu khong no chet cam vi denyAll")
                .isTrue();
        return response;
    }

    private void guiDanhGia(String tenDangNhap) {
        HttpHeaders headers = bearer(tenDangNhap);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"maSach\":" + maSach + ",\"diemXepHang\":4,\"nhanXet\":\"Danh gia fixture\"}";
        ResponseEntity<String> response = rest.postForEntity("/api/danh-gia/them-danh-gia-v1",
                new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("fixture: gui danh gia thanh cong")
                .isTrue();
    }

    private void anDanhGiaMoiNhat() {
        Long maDanhGia = suDanhGiaRepository.findAll().stream()
                .filter(d -> d.getSach() != null && d.getSach().getMaSach() == maSach)
                .map(d -> d.getMaDanhGia())
                .findFirst()
                .orElseThrow(() -> new AssertionError("khong tim thay danh gia de an"));
        ResponseEntity<String> response = rest.postForEntity(
                "/api/admin/danh-gia/unactive/{id}", new HttpEntity<>(bearer(quanTri)),
                String.class, maDanhGia);
        assertThat(response.getStatusCode().is2xxSuccessful()).as("admin an duoc danh gia").isTrue();
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
            sach.setTenSach("Co The Danh Gia Book " + System.nanoTime());
            sach.setTenTacGia("Fixture");
            sach.setMoTa("Sach fixture cho CoTheDanhGiaIT");
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
            user.setHoDem("CoThe");
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
