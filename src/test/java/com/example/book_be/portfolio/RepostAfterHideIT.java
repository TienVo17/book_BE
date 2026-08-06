package com.example.book_be.portfolio;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.danhgia.domain.TrangThaiDanhGia;
import com.example.book_be.danhgia.repository.SuDanhGiaRepository;
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
 * Vong lap "admin an → tac gia tu xoa → dang lai" phai bi chan.
 *
 * <p>Bat bien cua phase 1 ("danh gia bi an khong truy cap duoc") KHONG dong duoc lo nay:
 * no chi noi ve kha nang doc. Chu so huu van xoa duoc bai cua chinh minh — va do la
 * quyen dung dan — nhung khi dong bien mat thi rang buoc duy nhat (nguoi, sach) duoc
 * giai phong, don DA_GIAO van con, va dieu kien danh gia lai thoa. Khong co gi chan thi
 * kiem duyet chi ton them mot cu click cho nguoi bi kiem duyet.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class RepostAfterHideIT {

    private static final String MAT_KHAU = "RepostAfterHide@123";

    @Autowired TestRestTemplate rest;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired QuyenRepository quyenRepository;
    @Autowired SuDanhGiaRepository suDanhGiaRepository;
    @Autowired SachRepository sachRepository;
    @Autowired DonHangRepository donHangRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager txManager;

    private final List<Integer> donDaTao = new ArrayList<>();
    private final List<Integer> sachDaTao = new ArrayList<>();
    private String tacGia;
    private String quanTri;
    private int maSach;

    @BeforeEach
    void provisionFixtures() {
        long runId = System.nanoTime();
        tacGia = taoNguoiDung("repost-author-" + runId, "USER");
        quanTri = taoNguoiDung("repost-admin-" + runId, "ADMIN");
        maSach = taoSach();
        donDaTao.add(DonHangDaGiaoFixture.taoDonDaGiao(txManager, donHangRepository,
                nguoiDungRepository, sachRepository, tacGia, maSach));
    }

    @AfterEach
    void cleanupFixtures() {
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                suDanhGiaRepository.findAll().stream()
                        .filter(d -> d.getSach() != null && sachDaTao.contains(d.getSach().getMaSach()))
                        .forEach(suDanhGiaRepository::delete));
        donDaTao.forEach(ma -> DonHangDaGiaoFixture.xoaDon(txManager, donHangRepository, ma));
        donDaTao.clear();
        xoaNguoiDung(tacGia);
        xoaNguoiDung(quanTri);
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                sachDaTao.forEach(ma -> sachRepository.findById((long) ma).ifPresent(sachRepository::delete)));
        sachDaTao.clear();
    }

    @Test
    void an_roi_tu_xoa_roi_dang_lai_bi_chan() {
        long maDanhGia = guiDanhGia("Bai goc se bi kiem duyet");
        anDanhGia(maDanhGia);
        tuXoa(maDanhGia);

        ResponseEntity<String> dangLai = guiDanhGiaThoRaw("Bai dang lai sau khi bi an");

        assertThat(dangLai.getStatusCode().value())
                .as("dau kiem duyet phai song sot qua buoc tac gia tu xoa bai")
                .isEqualTo(403);
        assertThat(dangLai.getBody() == null ? "" : dangLai.getBody())
                .contains("\"code\":\"FORBIDDEN\"");
    }

    /** Tombstone chi chan dung cap (nguoi, sach) do, khong phai toan bo hoat dong cua nguoi do. */
    @Test
    void bi_chan_o_mot_cuon_khong_lam_mat_quyen_danh_gia_cuon_khac() {
        long maDanhGia = guiDanhGia("Bai goc se bi kiem duyet");
        anDanhGia(maDanhGia);
        tuXoa(maDanhGia);

        int maSachKhac = taoSach();
        donDaTao.add(DonHangDaGiaoFixture.taoDonDaGiao(txManager, donHangRepository,
                nguoiDungRepository, sachRepository, tacGia, maSachKhac));

        HttpHeaders headers = bearer(tacGia);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity("/api/danh-gia/them-danh-gia-v1",
                new HttpEntity<>("{\"maSach\":" + maSachKhac
                        + ",\"diemXepHang\":5,\"nhanXet\":\"Cuon khac, khong lien quan\"}", headers),
                String.class);

        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("tombstone gan voi cap (nguoi, sach), khong phai voi nguoi")
                .isTrue();
    }

    /**
     * Duong lach thu hai, re hon nhieu: neu sua bai ma reset duoc trang thai kiem duyet
     * thi tac gia chang can xoa gi ca, chi can bam "sua".
     */
    @Test
    void sua_bai_khong_go_duoc_trang_thai_da_an() {
        long maDanhGia = guiDanhGia("Bai goc se bi kiem duyet");
        anDanhGia(maDanhGia);

        HttpHeaders headers = bearer(tacGia);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> sua = rest.postForEntity("/api/danh-gia/sua-danh-gia/{id}",
                new HttpEntity<>("{\"diemXepHang\":5,\"nhanXet\":\"Sua lai cho hien len\"}", headers),
                String.class, maDanhGia);
        assertThat(sua.getStatusCode().is2xxSuccessful()).as("chu so huu sua duoc bai cua minh").isTrue();

        SuDanhGia sauKhiSua = suDanhGiaRepository.findById(maDanhGia).orElseThrow();
        assertThat(sauKhiSua.getTrangThai())
                .as("sua noi dung khong duoc dong thoi go trang thai kiem duyet")
                .isEqualTo(TrangThaiDanhGia.DA_AN);
    }

    // ---------------------------------------------------------------------

    private long guiDanhGia(String nhanXet) {
        ResponseEntity<String> response = guiDanhGiaThoRaw(nhanXet);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("fixture: bai dau tien phai dang duoc")
                .isTrue();
        return suDanhGiaRepository.findAll().stream()
                .filter(d -> d.getSach() != null && d.getSach().getMaSach() == maSach)
                .map(SuDanhGia::getMaDanhGia)
                .findFirst()
                .orElseThrow(() -> new AssertionError("khong tim thay danh gia vua tao"));
    }

    private ResponseEntity<String> guiDanhGiaThoRaw(String nhanXet) {
        HttpHeaders headers = bearer(tacGia);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity("/api/danh-gia/them-danh-gia-v1",
                new HttpEntity<>("{\"maSach\":" + maSach + ",\"diemXepHang\":1,\"nhanXet\":\""
                        + nhanXet + "\"}", headers), String.class);
    }

    private void anDanhGia(long maDanhGia) {
        ResponseEntity<String> response = rest.postForEntity("/api/admin/danh-gia/unactive/{id}",
                new HttpEntity<>(bearer(quanTri)), String.class, maDanhGia);
        assertThat(response.getStatusCode().is2xxSuccessful()).as("admin an duoc danh gia").isTrue();
    }

    private void tuXoa(long maDanhGia) {
        ResponseEntity<String> response = rest.postForEntity("/api/danh-gia/xoa-danh-gia/{id}",
                new HttpEntity<>(bearer(tacGia)), String.class, maDanhGia);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("chu so huu van phai xoa duoc bai cua minh — cam xoa khong phai cach chan")
                .isTrue();
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
            sach.setTenSach("Repost Fixture Book " + System.nanoTime());
            sach.setTenTacGia("Fixture");
            sach.setMoTa("Sach fixture cho RepostAfterHideIT");
            sach.setGiaNiemYet(mau.getGiaNiemYet());
            sach.setGiaBan(mau.getGiaBan());
            sach.setSoLuong(100);
            sach.setTrungBinhXepHang(0);
            sach.setSoLuotDanhGia(0);
            sach.setIsActive(1);
            int ma = sachRepository.saveAndFlush(sach).getMaSach();
            sachDaTao.add(ma);
            return ma;
        });
    }

    private String taoNguoiDung(String tenDangNhap, String tenQuyen) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Quyen quyen = quyenRepository.findByTenQuyen(tenQuyen);
            assertThat(quyen).as("quyen %s da duoc seed", tenQuyen).isNotNull();

            NguoiDung user = new NguoiDung();
            user.setHoDem("Repost");
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
