package com.example.book_be.portfolio;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.danhgia.domain.TrangThaiDanhGia;
import com.example.book_be.danhgia.repository.SuDanhGiaRepository;
import com.example.book_be.danhgia.service.DanhGiaService;
import com.example.book_be.donhang.repository.DonHangRepository;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.domain.Quyen;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.repository.QuyenRepository;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.sach.repository.SachRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Duong doc cong khai: phan trang, sap xep, loc theo sao, phan bo sao.
 *
 * <p>Truoc phase nay, {@code findAll} tra ve TOAN BO danh gia cua mot cuon sach trong mot
 * mang khong gioi han — mot cuon sach thanh cong se tu bien trang san pham cua no thanh
 * mot response hang megabyte.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class DanhGiaPagingIT {

    private static final String MAT_KHAU = "DanhGiaPaging@123";
    private static final int SO_DANH_GIA = 25;

    @Autowired TestRestTemplate rest;
    @Autowired DanhGiaService danhGiaService;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired QuyenRepository quyenRepository;
    @Autowired SuDanhGiaRepository suDanhGiaRepository;
    @Autowired SachRepository sachRepository;
    @Autowired DonHangRepository donHangRepository;
    @Autowired PlatformTransactionManager txManager;
    @Autowired org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder passwordEncoder;

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> nguoiDungDaTao = new ArrayList<>();
    private final List<Integer> donDaTao = new ArrayList<>();
    private int maSach;

    @BeforeEach
    void provisionFixtures() {
        maSach = taoSach();
        // 25 danh gia, diem xoay vong 1..5 nen moi muc sao co so luong biet truoc.
        for (int i = 0; i < SO_DANH_GIA; i++) {
            themDanhGia((i % 5) + 1);
        }
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
    void phan_trang_dung_so_dong_va_so_trang() {
        JsonNode trangDau = goi("&page=0&size=10");

        assertThat(trangDau.get("content").size()).isEqualTo(10);
        assertThat(trangDau.get("tongSoTrang").asInt()).isEqualTo(3);
        assertThat(trangDau.get("tongSo").asLong()).isEqualTo(SO_DANH_GIA);

        JsonNode trangCuoi = goi("&page=2&size=10");
        assertThat(trangCuoi.get("content").size())
                .as("trang cuoi chi con phan du")
                .isEqualTo(5);
    }

    /** Kich thuoc trang la tai cua may chu, khong phai lua chon cua client. */
    @Test
    void kich_thuoc_trang_bi_chan_tren() {
        JsonNode trang = goi("&page=0&size=100000");

        assertThat(trang.get("kichThuoc").asInt()).isLessThanOrEqualTo(50);
    }

    @Test
    void sap_xep_theo_diem_cao_va_diem_thap() {
        JsonNode cao = goi("&sort=diem-cao&size=5");
        assertThat(diemCua(cao)).allMatch(diem -> diem == 5);

        JsonNode thap = goi("&sort=diem-thap&size=5");
        assertThat(diemCua(thap)).allMatch(diem -> diem == 1);
    }

    @Test
    void loc_theo_sao_chi_tra_dung_muc_sao_do() {
        JsonNode trang = goi("&loc=4&size=50");

        assertThat(diemCua(trang)).isNotEmpty().allMatch(diem -> diem == 4);
    }

    /**
     * Cai bay de sai nhat cua man hinh nay. Neu phan bo duoc tinh lai theo bo loc dang
     * chon thi vua bam vao cot "4 sao", bon cot con lai se ve 0 va thanh phan bo tu pha
     * huy chinh cong dung cua no.
     */
    @Test
    void phan_bo_khong_doi_theo_bo_loc() {
        JsonNode khongLoc = goi("&size=10");
        JsonNode coLoc = goi("&loc=4&size=10");

        assertThat(coLoc.get("phanBo"))
                .as("phan bo luon tinh tren toan bo danh gia hien thi")
                .isEqualTo(khongLoc.get("phanBo"));
        assertThat(coLoc.get("tongSo").asLong())
                .as("tongSo cung la tong toan bo, khong phai so dong khop bo loc")
                .isEqualTo(SO_DANH_GIA);
    }

    @Test
    void tong_phan_bo_bang_tong_so_va_du_nam_khoa() {
        JsonNode trang = goi("&size=10");
        JsonNode phanBo = trang.get("phanBo");

        long tong = 0;
        for (int sao = 1; sao <= 5; sao++) {
            assertThat(phanBo.has(String.valueOf(sao)))
                    .as("khoa %d phai co mat ke ca khi bang 0, neu khong giao dien mat thanh", sao)
                    .isTrue();
            tong += phanBo.get(String.valueOf(sao)).asLong();
        }
        assertThat(tong).isEqualTo(trang.get("tongSo").asLong());
    }

    @Test
    void danh_gia_bi_an_khong_lot_qua_trang_bo_loc_hay_kieu_sap_xep_nao() {
        Long maDanhGiaBiAn = suDanhGiaRepository.findAll().stream()
                .filter(d -> d.getSach() != null && d.getSach().getMaSach() == maSach)
                .filter(d -> d.getDiemXepHang() == 4F)
                .map(SuDanhGia::getMaDanhGia)
                .findFirst()
                .orElseThrow();
        String noiDung = suDanhGiaRepository.findById(maDanhGiaBiAn).orElseThrow().getNhanXet();
        danhGiaService.doiTrangThai(maDanhGiaBiAn, TrangThaiDanhGia.DA_AN);

        for (String truyVan : List.of("&size=50", "&loc=4&size=50", "&sort=diem-cao&size=50",
                "&sort=diem-thap&size=50", "&sort=cu-nhat&size=50", "&sort=huu-ich&size=50")) {
            assertThat(goi(truyVan).toString())
                    .as("danh gia da an khong duoc lo qua %s", truyVan)
                    .doesNotContain(noiDung);
        }

        assertThat(goi("&size=50").get("tongSo").asLong())
                .as("danh gia da an cung khong duoc dem vao tong")
                .isEqualTo(SO_DANH_GIA - 1);
    }

    /** DTO cong khai khong duoc mang dinh danh noi bo cua nguoi viet. */
    @Test
    void dto_cong_khai_khong_chua_ma_nguoi_dung() {
        assertThat(goi("&size=50").toString())
                .doesNotContain("maNguoiDung")
                .doesNotContain("isActive");
    }

    // ---------------------------------------------------------------------

    private JsonNode goi(String thamSoThem) {
        String body = rest.getForObject("/api/danh-gia?maSach=" + maSach + thamSoThem, String.class);
        try {
            return mapper.readTree(body == null ? "{}" : body);
        } catch (Exception e) {
            throw new AssertionError("response khong phai JSON hop le: " + body, e);
        }
    }

    private List<Integer> diemCua(JsonNode trang) {
        List<Integer> diem = new ArrayList<>();
        trang.get("content").forEach(dong -> diem.add(dong.get("diemXepHang").asInt()));
        return diem;
    }

    private void themDanhGia(int diem) {
        String tenDangNhap = taoNguoiDung();
        donDaTao.add(DonHangDaGiaoFixture.taoDonDaGiao(txManager, donHangRepository,
                nguoiDungRepository, sachRepository, tenDangNhap, maSach));
        Long maNguoiDung = new TransactionTemplate(txManager).execute(status ->
                (long) nguoiDungRepository.findByTenDangNhap(tenDangNhap).getMaNguoiDung());
        danhGiaService.addReview("Danh gia so " + tenDangNhap, diem, maNguoiDung, (long) maSach);
    }

    private int taoSach() {
        return new TransactionTemplate(txManager).execute(status -> {
            Sach mau = sachRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("can it nhat mot cuon sach seed"));
            Sach sach = new Sach();
            sach.setTenSach("Paging Fixture Book " + System.nanoTime());
            sach.setTenTacGia("Fixture");
            sach.setMoTa("Sach fixture cho DanhGiaPagingIT");
            sach.setGiaNiemYet(mau.getGiaNiemYet());
            sach.setGiaBan(mau.getGiaBan());
            sach.setSoLuong(1000);
            sach.setTrungBinhXepHang(0);
            sach.setSoLuotDanhGia(0);
            sach.setIsActive(1);
            return sachRepository.saveAndFlush(sach).getMaSach();
        });
    }

    private String taoNguoiDung() {
        String tenDangNhap = "paging-" + System.nanoTime();
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Quyen quyen = quyenRepository.findByTenQuyen("USER");
            NguoiDung user = new NguoiDung();
            user.setHoDem("Paging");
            user.setTen("Fixture");
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
