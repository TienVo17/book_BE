package com.example.book_be.portfolio;

import com.example.book_be.TestcontainersConfig;
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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Moi nguoi mot danh gia moi cuon sach — rang buoc {@code uk_danhgia_nguoi_sach} cua V11.
 *
 * <p>Truoc day khong co gi chan mot tai khoan danh gia cung mot cuon sach bao nhieu lan tuy
 * y, tuc la diem trung binh hien tren trang san pham co the bi mot nguoi keo len hoac dim
 * xuong tuy thich. Test nay bat ca hai mat: HTTP tra 409 co ma loi, va database chi con
 * dung mot dong.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class DanhGiaUniqueIT {

    private static final String MAT_KHAU = "DanhGiaUnique@123";

    @Autowired TestRestTemplate rest;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired QuyenRepository quyenRepository;
    @Autowired SuDanhGiaRepository suDanhGiaRepository;
    @Autowired SachRepository sachRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager txManager;

    private String nguoiDanhGia;
    private String quanTri;
    private int maSach;

    @BeforeEach
    void provisionFixtures() {
        long runId = System.nanoTime();
        nguoiDanhGia = taoNguoiDung("unique-user-" + runId, "USER");
        quanTri = taoNguoiDung("unique-admin-" + runId, "ADMIN");
        maSach = taoSach();
    }

    @AfterEach
    void cleanupFixtures() {
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                suDanhGiaRepository.findAll().stream()
                        .filter(d -> d.getSach() != null && d.getSach().getMaSach() == maSach)
                        .forEach(suDanhGiaRepository::delete));
        xoaNguoiDung(nguoiDanhGia);
        xoaNguoiDung(quanTri);
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                sachRepository.findById((long) maSach).ifPresent(sachRepository::delete));
    }

    @Test
    void danh_gia_lan_hai_cung_mot_cuon_sach_bi_tu_choi() {
        assertThat(guiDanhGia(5, "Danh gia dau tien").getStatusCode().is2xxSuccessful())
                .as("danh gia dau tien phai thanh cong")
                .isTrue();

        ResponseEntity<String> lanHai = guiDanhGia(1, "Danh gia thu hai cua cung mot nguoi");

        assertThat(lanHai.getStatusCode().value())
                .as("danh gia trung phai la 409, khong phai 500")
                .isEqualTo(409);
        assertThat(lanHai.getBody() == null ? "" : lanHai.getBody())
                .as("409 phai theo dung lugc do loi thong nhat")
                .contains("\"code\":\"CONFLICT\"");
    }

    /** Rang buoc chi co gia tri neu no thuc su chan dong thu hai xuong database. */
    @Test
    void chi_con_dung_mot_dong_trong_database() {
        guiDanhGia(5, "Danh gia dau tien");
        guiDanhGia(1, "Danh gia thu hai cua cung mot nguoi");

        long soDong = suDanhGiaRepository.findAll().stream()
                .filter(d -> d.getSach() != null && d.getSach().getMaSach() == maSach)
                .count();

        assertThat(soDong)
                .as("lan gui thu hai khong duoc de lai dong nao")
                .isEqualTo(1);
    }

    /**
     * Duong an/hien cua admin voi id khong ton tai. Ban truoc dung {@code orElse(null)} roi
     * goi setter ngay, nen id la se sinh NullPointerException va tra ve 500.
     */
    @Test
    void admin_an_danh_gia_khong_ton_tai_tra_404_co_ma_loi() {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/admin/danh-gia/unactive/{id}", new HttpEntity<>(bearer(quanTri)),
                String.class, 999_999_999L);

        assertThat(response.getStatusCode().value())
                .as("id khong ton tai la 404, khong phai 500")
                .isEqualTo(404);
        assertThat(response.getBody() == null ? "" : response.getBody())
                .as("404 phai theo dung lugc do loi thong nhat")
                .contains("\"code\":\"NOT_FOUND\"");
    }

    private ResponseEntity<String> guiDanhGia(int diem, String nhanXet) {
        HttpHeaders headers = bearer(nguoiDanhGia);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"maSach\":" + maSach + ",\"diemXepHang\":" + diem
                + ",\"nhanXet\":\"" + nhanXet + "\"}";
        return rest.postForEntity("/api/danh-gia/them-danh-gia-v1",
                new HttpEntity<>(body, headers), String.class);
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
            sach.setTenSach("Unique Fixture Book " + System.nanoTime());
            sach.setTenTacGia("Fixture");
            sach.setMoTa("Sach fixture cho DanhGiaUniqueIT");
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
            user.setHoDem("Unique");
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
