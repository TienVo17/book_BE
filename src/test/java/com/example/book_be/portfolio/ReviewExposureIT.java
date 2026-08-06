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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mot danh gia da bi admin an phai bien mat khoi MOI duong cong khai.
 *
 * <p>Truoc ban va nay, `/sach/{id}/listDanhGia` (association cua Spring Data REST)
 * van tra ve danh gia da an kem nguyen co trang thai cu cho khach an danh, nen thao tac
 * kiem duyet cua admin khong co tac dung that. Test nay khoa ca hai duong lai.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class ReviewExposureIT {

    private static final String MAT_KHAU = "ReviewExposure@123";
    private static final String NOI_DUNG_BI_AN = "Noi dung nay da bi admin an va khong duoc lo ra ngoai";

    @Autowired TestRestTemplate rest;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired QuyenRepository quyenRepository;
    @Autowired SuDanhGiaRepository suDanhGiaRepository;
    @Autowired SachRepository sachRepository;
    @Autowired com.example.book_be.donhang.repository.DonHangRepository donHangRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager txManager;

    private String tacGia;
    private String quanTri;
    private Long maDanhGia;
    private Integer maSach;
    private Integer maDonHang;

    @BeforeEach
    void provisionFixtures() {
        long runId = System.nanoTime();
        tacGia = taoNguoiDung("exposure-author-" + runId, "USER");
        quanTri = taoNguoiDung("exposure-admin-" + runId, "ADMIN");
        taoDanhGia(tacGia);
    }

    @AfterEach
    void cleanupFixtures() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            if (maDanhGia != null) {
                suDanhGiaRepository.findById(maDanhGia).ifPresent(suDanhGiaRepository::delete);
            }
        });
        DonHangDaGiaoFixture.xoaDon(txManager, donHangRepository, maDonHang);
        maDonHang = null;
        xoaNguoiDung(tacGia);
        xoaNguoiDung(quanTri);
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            if (maSach != null) {
                sachRepository.findById((long) maSach).ifPresent(sachRepository::delete);
            }
        });
    }

    @Test
    void danh_gia_bi_an_khong_lo_qua_endpoint_cong_khai() {
        anDanhGia();

        ResponseEntity<String> response = rest.getForEntity(
                "/api/danh-gia?maSach={maSach}", String.class, maSach);

        assertThat(response.getBody()).doesNotContain(NOI_DUNG_BI_AN);
    }

    /**
     * Duong thu hai. Day la duong that su bi ro ri truoc khi sua — no khong di qua
     * DanhGiaController nen khong chiu bat ky bo loc trang thai nao.
     */
    @Test
    void danh_gia_bi_an_khong_lo_qua_quan_he_spring_data_rest() {
        anDanhGia();

        ResponseEntity<String> response = rest.getForEntity(
                "/sach/{maSach}/listDanhGia", String.class, maSach);

        assertThat(response.getBody() == null ? "" : response.getBody())
                .as("quan he listDanhGia khong duoc lo danh gia da bi an")
                .doesNotContain(NOI_DUNG_BI_AN);
        assertThat(response.getStatusCode().value())
                .as("dong endpoint phai tra 404 sach se, khong phai 500 do exception khong ai bat")
                .isEqualTo(404);
    }

    /**
     * Duong thu ba, de bo sot nhat: chinh body cua {@code GET /sach/{id}}. Dong association
     * ma van de Spring Data REST serialize danh gia inline trong tai nguyen sach thi noi dung
     * bi an van ro ri, chi doi cho.
     */
    @Test
    void tai_nguyen_sach_khong_nhung_danh_gia_trong_body() {
        anDanhGia();

        ResponseEntity<String> response = rest.getForEntity("/sach/{maSach}", String.class, maSach);

        assertThat(response.getStatusCode().value())
                .as("tai nguyen sach van phai doc duoc")
                .isEqualTo(200);
        assertThat(response.getBody() == null ? "" : response.getBody())
                .as("body cua sach khong duoc nhung noi dung danh gia da bi an")
                .doesNotContain(NOI_DUNG_BI_AN);
    }

    /** Khach an danh khong duoc doc thang mot danh gia qua repository resource. */
    @Test
    void repository_resource_cua_danh_gia_khong_doc_duoc_cong_khai() {
        anDanhGia();

        ResponseEntity<String> response = rest.getForEntity(
                "/su-danh-gia/{id}", String.class, maDanhGia);

        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("/su-danh-gia/{id} khong duoc mo cong khai")
                .isFalse();
    }

    private void anDanhGia() {
        HttpHeaders headers = bearer(quanTri);
        ResponseEntity<String> response = rest.postForEntity(
                "/api/admin/danh-gia/unactive/{id}", new HttpEntity<>(headers), String.class, maDanhGia);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("admin an duoc danh gia")
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

    /**
     * Sach RIENG cho moi lan chay. Neu muon danh gia vao cuon seed dau tien, buoc an
     * danh gia se doi trung binh/so luot cua cuon do, va {@code @AfterEach} xoa dong
     * danh gia mà khong tinh lai — de lai du lieu tong hop sai cho moi test chay sau.
     */
    private void taoDanhGia(String tenDangNhap) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            NguoiDung nguoiDung = nguoiDungRepository.findByTenDangNhap(tenDangNhap);
            Sach mau = sachRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("can it nhat mot cuon sach seed"));
            Sach sach = new Sach();
            sach.setTenSach("Exposure Fixture Book " + System.nanoTime());
            sach.setTenTacGia("Fixture");
            sach.setMoTa("Sach fixture cho ReviewExposureIT");
            sach.setGiaNiemYet(mau.getGiaNiemYet());
            sach.setGiaBan(mau.getGiaBan());
            sach.setSoLuong(100);
            sach.setTrungBinhXepHang(0);
            sach.setSoLuotDanhGia(0);
            sach.setIsActive(1);
            sach = sachRepository.saveAndFlush(sach);

            // Bang chung da mua: phase 7 nang ma_don_hang len NOT NULL, va fixture nao con
            // dung danh gia khong don se chet o do. Gan luon o day thay vi doi.
            maDonHang = DonHangDaGiaoFixture.taoDonDaGiao(txManager, donHangRepository,
                    nguoiDungRepository, sachRepository, tenDangNhap, sach.getMaSach());

            SuDanhGia danhGia = new SuDanhGia();
            danhGia.setMaDonHang(maDonHang);
            danhGia.setNhanXet(NOI_DUNG_BI_AN);
            danhGia.setDiemXepHang(1F);
            danhGia.setTimestamp(new Timestamp(System.currentTimeMillis()));
            danhGia.datTrangThai(com.example.book_be.danhgia.domain.TrangThaiDanhGia.HIEN_THI);
            danhGia.setNguoiDung(nguoiDung);
            danhGia.setSach(sach);

            SuDanhGia daLuu = suDanhGiaRepository.saveAndFlush(danhGia);
            maDanhGia = daLuu.getMaDanhGia();
            maSach = sach.getMaSach();
        });
    }

    private String taoNguoiDung(String tenDangNhap, String tenQuyen) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Quyen quyen = quyenRepository.findByTenQuyen(tenQuyen);
            assertThat(quyen).as("quyen %s da duoc seed", tenQuyen).isNotNull();

            NguoiDung user = new NguoiDung();
            user.setHoDem("Exposure");
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
