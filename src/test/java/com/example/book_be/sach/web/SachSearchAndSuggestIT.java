package com.example.book_be.sach.web;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.sach.domain.HinhAnh;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.sach.dto.SachAdminUpsertBo;
import com.example.book_be.sach.repository.HinhAnhRepository;
import com.example.book_be.sach.repository.SachRepository;
import com.example.book_be.sach.service.SachService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GET /api/sach mo rong (size/giaMin/giaMax/sort) va GET /api/sach/goi-y.
 *
 * <p>Moi fixture duoc dat ten voi mot tien to rieng cho tung test run de loc rieng khoi du
 * lieu seed qua tham so {@code tensach}/{@code q} — tranh phu thuoc vao so luong sach seed co
 * san.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class SachSearchAndSuggestIT {

    @Autowired TestRestTemplate rest;
    @Autowired SachService sachService;
    @Autowired SachRepository sachRepository;
    @Autowired HinhAnhRepository hinhAnhRepository;

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Integer> sachFixtures = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Integer maSach : sachFixtures) {
            if (sachRepository.existsById(maSach.longValue())) {
                sachRepository.deleteById(maSach.longValue());
            }
        }
        sachFixtures.clear();
    }

    // --- sort ---------------------------------------------------------------------------

    @Test
    void sort_gia_tang_sap_xep_dung_thu_tu() {
        String tienTo = "SortFixGiaTang" + System.nanoTime();
        taoBoBaFixture(tienTo);

        JsonNode trang = goi("/api/sach?page=0&tensach=" + tienTo + "&sort=gia-tang");

        assertThat(tenSachTheoThuTu(trang)).containsExactly(
                tienTo + "Apple", tienTo + "Cherry", tienTo + "Banana");
    }

    @Test
    void sort_gia_giam_sap_xep_dung_thu_tu() {
        String tienTo = "SortFixGiaGiam" + System.nanoTime();
        taoBoBaFixture(tienTo);

        JsonNode trang = goi("/api/sach?page=0&tensach=" + tienTo + "&sort=gia-giam");

        assertThat(tenSachTheoThuTu(trang)).containsExactly(
                tienTo + "Banana", tienTo + "Cherry", tienTo + "Apple");
    }

    @Test
    void sort_ten_az_sap_xep_dung_thu_tu() {
        String tienTo = "SortFixTenAz" + System.nanoTime();
        taoBoBaFixture(tienTo);

        JsonNode trang = goi("/api/sach?page=0&tensach=" + tienTo + "&sort=ten-az");

        assertThat(tenSachTheoThuTu(trang)).containsExactly(
                tienTo + "Apple", tienTo + "Banana", tienTo + "Cherry");
    }

    @Test
    void sort_danh_gia_sap_xep_dung_thu_tu() {
        String tienTo = "SortFixDanhGia" + System.nanoTime();
        taoBoBaFixture(tienTo);

        JsonNode trang = goi("/api/sach?page=0&tensach=" + tienTo + "&sort=danh-gia");

        assertThat(tenSachTheoThuTu(trang)).containsExactly(
                tienTo + "Banana", tienTo + "Apple", tienTo + "Cherry");
    }

    @Test
    void mac_dinh_moi_nhat_giu_hanh_vi_cu_la_maSach_giam_dan() {
        String tienTo = "SortFixMacDinh" + System.nanoTime();
        taoBoBaFixture(tienTo);

        JsonNode trang = goi("/api/sach?page=0&tensach=" + tienTo);

        assertThat(tenSachTheoThuTu(trang)).containsExactly(
                tienTo + "Cherry", tienTo + "Banana", tienTo + "Apple");
    }

    @Test
    void sort_khong_hop_le_tra_400() {
        ResponseEntity<String> response = rest.getForEntity(
                "/api/sach?page=0&sort=khong-ton-tai", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- giaMin / giaMax ------------------------------------------------------------------

    @Test
    void loc_theo_khoang_gia_tra_dung_sach_trong_khoang() {
        String tienTo = "PriceFix" + System.nanoTime();
        taoBoBaFixture(tienTo);

        JsonNode trang = goi("/api/sach?page=0&tensach=" + tienTo + "&giaMin=150000&giaMax=250000");

        assertThat(tenSachTheoThuTu(trang)).containsExactly(tienTo + "Cherry");
    }

    @Test
    void gia_min_lon_hon_gia_max_tra_400() {
        ResponseEntity<String> response = rest.getForEntity(
                "/api/sach?page=0&giaMin=500000&giaMax=1000", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- chu D dac biet (Đ/đ) ---------------------------------------------------------------
    // Do bang request that: collation MySQL da tu bo qua moi dau thanh/dau mu, nhung "Đ" la
    // mot chu cai rieng trong bang chu cai tieng Viet nen KHONG duoc gop voi "D". Cac test nay
    // khang dinh ca hai chieu go (co dau / khong dau) deu khop, va cac dau khac van chay dung
    // nhu truoc (chong hoi quy).

    @Test
    void go_khong_dau_van_khop_ten_sach_co_chu_D() {
        String tienTo = "DFixTitle" + System.nanoTime();
        taoSach("Đắc Nhân Tâm " + tienTo, "Fixture author", 100000, 0, 1);

        JsonNode trang = goi("/api/sach?page=0&tensach=" + enc("dac nhan tam " + tienTo));

        assertThat(tenSachTheoThuTu(trang)).containsExactly("Đắc Nhân Tâm " + tienTo);
    }

    @Test
    void go_co_dau_chu_D_van_khop_nhu_truoc() {
        String tienTo = "DFixTitleAccented" + System.nanoTime();
        taoSach("Đắc Nhân Tâm " + tienTo, "Fixture author", 100000, 0, 1);

        JsonNode trang = goi("/api/sach?page=0&tensach=" + enc("Đắc Nhân Tâm " + tienTo));

        assertThat(tenSachTheoThuTu(trang)).containsExactly("Đắc Nhân Tâm " + tienTo);
    }

    @Test
    void go_khong_dau_van_khop_chu_D_trong_ten_tac_gia() {
        String tienTo = "DFixAuthor" + System.nanoTime();
        taoSach("Sach khong lien quan " + tienTo, "Đặng Tac Gia " + tienTo, 100000, 0, 1);

        JsonNode trang = goi("/api/sach?page=0&tensach=" + enc("dang tac gia " + tienTo));

        assertThat(tenSachTheoThuTu(trang)).containsExactly("Sach khong lien quan " + tienTo);
    }

    @Test
    void goi_y_go_khong_dau_van_khop_chu_D() {
        String tienTo = "DFixSuggest" + System.nanoTime();
        taoSach("Đắc Nhân Tâm " + tienTo, "Fixture author", 100000, 0, 1);

        JsonNode ketQua = goiY("/api/sach/goi-y?q=" + enc("dac nhan tam " + tienTo) + "&limit=10");

        assertThat(tenSachTuMang(ketQua)).contains("Đắc Nhân Tâm " + tienTo);
    }

    /** Chong hoi quy: cac dau thanh/dau mu KHAC (khong phai Đ) van phai tiep tuc khop nhu truoc. */
    @Test
    void go_khong_dau_van_khop_cac_dau_thanh_khac_ngoai_chu_D() {
        String tienTo = "AccentFix" + System.nanoTime();
        taoSach("Lược Sử " + tienTo, "Fixture author", 100000, 0, 1);

        JsonNode trang = goi("/api/sach?page=0&tensach=" + enc("luoc su " + tienTo));

        assertThat(tenSachTheoThuTu(trang)).containsExactly("Lược Sử " + tienTo);
    }

    // --- size clamp -----------------------------------------------------------------------

    @Test
    void size_qua_lon_bi_kep_ve_48() {
        JsonNode trang = goi("/api/sach?page=0&size=999");

        assertThat(trang.get("size").asInt()).isEqualTo(48);
    }

    @Test
    void size_bang_khong_bi_kep_ve_1() {
        JsonNode trang = goi("/api/sach?page=0&size=0");

        assertThat(trang.get("size").asInt()).isEqualTo(1);
    }

    // --- goi-y ------------------------------------------------------------------------------

    @Test
    void goi_y_mot_ky_tu_tra_ve_rong_ngay_khong_cham_database() {
        ResponseEntity<String> response = rest.getForEntity("/api/sach/goi-y?q=a", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("[]");
    }

    @Test
    void goi_y_rong_sau_khi_trim_tra_ve_rong() {
        ResponseEntity<String> response = rest.getForEntity("/api/sach/goi-y?q=%20%20", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("[]");
    }

    @Test
    void goi_y_sach_da_an_khong_xuat_hien() {
        String tienTo = "GoiYFixHidden" + System.nanoTime();
        taoSach(tienTo + "Visible", "Tac gia hien thi", 100000, 0, 1);
        taoSach(tienTo + "Hidden", "Tac gia an", 100000, 0, 0);

        JsonNode ketQua = goiY("/api/sach/goi-y?q=" + tienTo + "&limit=10");

        List<String> tenSachs = tenSachTuMang(ketQua);
        assertThat(tenSachs).contains(tienTo + "Visible");
        assertThat(tenSachs).doesNotContain(tienTo + "Hidden");
    }

    @Test
    void goi_y_khop_ca_ten_tac_gia() {
        String tienTo = "GoiYFixAuthor" + System.nanoTime();
        taoSach("Sach khong lien quan " + tienTo, tienTo + "AuthorMatch", 100000, 0, 1);

        JsonNode ketQua = goiY("/api/sach/goi-y?q=" + tienTo + "AuthorMatch&limit=10");

        assertThat(ketQua.size()).isEqualTo(1);
    }

    @Test
    void goi_y_limit_duoc_kep_trong_1_va_10() {
        String tienTo = "GoiYFixLimit" + System.nanoTime();
        for (int i = 0; i < 15; i++) {
            taoSach(tienTo + "Book" + i, "Tac gia " + tienTo, 100000 + i, 0, 1);
        }

        JsonNode vuotQuaMuoi = goiY("/api/sach/goi-y?q=" + tienTo + "&limit=999");
        JsonNode duoiMot = goiY("/api/sach/goi-y?q=" + tienTo + "&limit=0");

        assertThat(vuotQuaMuoi.size()).isLessThanOrEqualTo(10);
        assertThat(duoiMot.size()).isLessThanOrEqualTo(1);
    }

    @Test
    void goi_y_response_chi_co_dung_nam_truong_khong_thua() {
        String tienTo = "GoiYFixShape" + System.nanoTime();
        taoSach(tienTo + "Shape", "Tac gia shape", 100000, 0, 1);

        JsonNode ketQua = goiY("/api/sach/goi-y?q=" + tienTo + "&limit=10");

        assertThat(ketQua.size()).isEqualTo(1);
        Set<String> truong = new java.util.HashSet<>();
        ketQua.get(0).fieldNames().forEachRemaining(truong::add);
        assertThat(truong).containsExactlyInAnyOrder("maSach", "tenSach", "slug", "urlAnh", "giaBan");
    }

    @Test
    void goi_y_lay_anh_dau_tien_theo_thu_tu_them_vao_va_null_khi_khong_co_anh() {
        String tienTo = "GoiYFixImage" + System.nanoTime();
        Sach coAnh = taoSach(tienTo + "CoAnh", "Tac gia anh", 100000, 0, 1);
        themAnh(coAnh.getMaSach(), "https://example.com/first.jpg");
        themAnh(coAnh.getMaSach(), "https://example.com/second.jpg");
        taoSach(tienTo + "KhongAnh", "Tac gia khong anh", 100000, 0, 1);

        JsonNode ketQua = goiY("/api/sach/goi-y?q=" + tienTo + "&limit=10");

        for (JsonNode item : ketQua) {
            if (item.get("tenSach").asText().equals(tienTo + "CoAnh")) {
                assertThat(item.get("urlAnh").asText()).isEqualTo("https://example.com/first.jpg");
            }
            if (item.get("tenSach").asText().equals(tienTo + "KhongAnh")) {
                assertThat(item.get("urlAnh").isNull()).isTrue();
            }
        }
    }

    // ---------------------------------------------------------------------------------------

    private void taoBoBaFixture(String tienTo) {
        taoSach(tienTo + "Apple", "Fixture author", 100000, 3.0, 1);
        taoSach(tienTo + "Banana", "Fixture author", 300000, 5.0, 1);
        taoSach(tienTo + "Cherry", "Fixture author", 200000, 1.0, 1);
    }

    private Sach taoSach(String tenSach, String tenTacGia, double giaBan, double trungBinhXepHang, int isActive) {
        SachAdminUpsertBo bo = new SachAdminUpsertBo();
        bo.setTenSach(tenSach);
        bo.setTenTacGia(tenTacGia);
        bo.setMoTaChiTiet("Search/suggest fixture description");
        bo.setGiaNiemYet(giaBan + 10000);
        bo.setGiaBan(giaBan);
        bo.setSoLuongTon(10);
        bo.setIsActive(isActive);
        bo.setMaTheLoaiList(List.of());
        bo.setListImageStr(List.of());
        Sach sach = sachService.save(bo);
        sachFixtures.add(sach.getMaSach());
        if (trungBinhXepHang != 0) {
            // Doc lai truoc khi sua de tranh cac van de merge tren entity da detached
            // vua tra ve tu mot transaction khac (@Transactional cua sachService.save da dong).
            Sach quanLy = sachRepository.findById((long) sach.getMaSach()).orElseThrow();
            quanLy.setTrungBinhXepHang(trungBinhXepHang);
            sachRepository.saveAndFlush(quanLy);
        }
        return sachRepository.findById((long) sach.getMaSach()).orElseThrow();
    }

    private void themAnh(int maSach, String urlHinh) {
        HinhAnh hinhAnh = new HinhAnh();
        // Chi can mot proxy tham chieu FK, khong can doc nguyen ca Sach — tranh moi van de
        // merge/detached khi gan association nguoc.
        hinhAnh.setSach(sachRepository.getReferenceById((long) maSach));
        hinhAnh.setUrlHinh(urlHinh);
        hinhAnh.setIcon(false);
        hinhAnhRepository.saveAndFlush(hinhAnh);
    }

    /**
     * Ma hoa gia tri tham so URL (dau cach, chu tieng Viet co dau) — TestRestTemplate khong tu
     * ma hoa chuoi truyen thang, va "+" trong query string duoc server giai ma lai thanh dau
     * cach nhu quy uoc chuan.
     */
    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private JsonNode goi(String url) {
        String body = rest.getForObject(url, String.class);
        try {
            return mapper.readTree(body == null ? "{}" : body);
        } catch (Exception e) {
            throw new AssertionError("response khong phai JSON hop le: " + body, e);
        }
    }

    private JsonNode goiY(String url) {
        return goi(url);
    }

    private List<String> tenSachTheoThuTu(JsonNode trang) {
        List<String> ten = new ArrayList<>();
        trang.get("content").forEach(dong -> ten.add(dong.get("tenSach").asText()));
        return ten;
    }

    private List<String> tenSachTuMang(JsonNode mang) {
        return StreamSupport.stream(mang.spliterator(), false)
                .map(dong -> dong.get("tenSach").asText())
                .toList();
    }
}
