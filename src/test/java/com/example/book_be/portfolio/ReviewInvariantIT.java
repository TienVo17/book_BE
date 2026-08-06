package com.example.book_be.portfolio;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.danhgia.domain.TrangThaiDanhGia;
import com.example.book_be.danhgia.repository.DanhGiaHinhAnhRepository;
import com.example.book_be.danhgia.repository.SuDanhGiaRepository;
import com.example.book_be.donhang.repository.DonHangRepository;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.domain.Quyen;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.repository.QuyenRepository;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.sach.repository.SachRepository;
import com.example.book_be.sach.service.CloudinaryService;
import com.example.book_be.sach.service.CloudinaryUploadResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mot vong doi danh gia day du chay lien tuc tren cung mot cuon sach.
 *
 * <p>Cac lop IT khac moi lop khoa mot bat bien rieng le, va deu bat dau tu trang thai
 * sach. Cai khong lop nao trong so do bat duoc la TUONG TAC: du lieu tong hop sau khi
 * sua roi an roi hien lai, binh chon va anh khong duoc lam lech no, va dau kiem duyet
 * phai song sot qua ca chuoi thao tac chu khong chi qua mot lan xoa.
 *
 * <p>Chi {@link CloudinaryService} duoc mock — day la ranh gioi mang duy nhat. Service
 * danh gia, repository va database deu la that; neu mock ca chung thi test chi con khang
 * dinh lai chinh cac mock cua no.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class ReviewInvariantIT {

    private static final String MAT_KHAU = "ReviewInvariant@123";
    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
    };
    private static final String URL_ANH = "https://cdn.example.test/reviews/invariant.png";
    private static final String PUBLIC_ID_ANH = "web-ban-sach/reviews/invariant";

    @Autowired TestRestTemplate rest;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired QuyenRepository quyenRepository;
    @Autowired SuDanhGiaRepository suDanhGiaRepository;
    @Autowired DanhGiaHinhAnhRepository hinhAnhRepository;
    @Autowired SachRepository sachRepository;
    @Autowired DonHangRepository donHangRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager txManager;

    @MockBean CloudinaryService cloudinaryService;

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> nguoiDungDaTao = new ArrayList<>();
    private final List<Integer> donDaTao = new ArrayList<>();
    private String chuSoHuu;
    private String nguoiBinhChon;
    private String quanTri;
    private int maSach;

    @BeforeEach
    void provisionFixtures() throws IOException {
        // HttpURLConnection mac dinh khong gui lai duoc mot POST khong body sau 401.
        rest.getRestTemplate().setRequestFactory(
                new JdkClientHttpRequestFactory(HttpClient.newHttpClient()));
        when(cloudinaryService.upload(
                any(byte[].class), any(), any(), eq(CloudinaryService.REVIEW_IMAGE_FOLDER)))
                .thenReturn(new CloudinaryUploadResult(URL_ANH, PUBLIC_ID_ANH));

        long runId = System.nanoTime();
        maSach = taoSach();
        chuSoHuu = taoNguoiDung("invariant-owner-" + runId, "USER");
        nguoiBinhChon = taoNguoiDung("invariant-voter-" + runId, "USER");
        quanTri = taoNguoiDung("invariant-admin-" + runId, "ADMIN");
        donDaTao.add(DonHangDaGiaoFixture.taoDonDaGiao(txManager, donHangRepository,
                nguoiDungRepository, sachRepository, chuSoHuu, maSach));
    }

    @AfterEach
    void cleanupFixtures() {
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                suDanhGiaRepository.findAll().stream()
                        .filter(d -> d.getSach() != null && d.getSach().getMaSach() == maSach)
                        .forEach(suDanhGiaRepository::delete));
        donDaTao.forEach(ma -> DonHangDaGiaoFixture.xoaDon(txManager, donHangRepository, ma));
        donDaTao.clear();
        // Xoa nguoi dung va sach keo theo tombstone qua cascade cua V12.
        nguoiDungDaTao.forEach(this::xoaNguoiDung);
        nguoiDungDaTao.clear();
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                sachRepository.findById((long) maSach).ifPresent(sachRepository::delete));
    }

    @Test
    void vong_doi_day_du_giu_nguyen_tong_hop_hien_thi_va_dau_kiem_duyet() throws IOException {
        // 1. Dang bai — tong hop phai theo ngay tu dong dau tien.
        long maDanhGia = dangBai(4);
        khangDinhTongHop(1, 4.0);

        // 2. Sua diem — duong de hong nhat neu thieu flush truoc khi tinh lai.
        suaBai(maDanhGia, 5, "Doc lai lan hai, hay hon nhieu");
        khangDinhTongHop(1, 5.0);

        // 3. Binh chon huu ich KHONG duoc cham vao diem trung binh.
        assertThat(binhChon(maDanhGia).getBody()).contains("\"soLuotHuuIch\":1");
        khangDinhTongHop(1, 5.0);

        // 4. Anh dinh kem cung khong phai du lieu xep hang.
        long maHinhAnh = taiAnh(maDanhGia, "invariant-upload");
        khangDinhTongHop(1, 5.0);
        JsonNode trangCoAnh = docTrangCongKhai();
        assertThat(trangCoAnh.toString())
                .contains(URL_ANH)
                .as("public id la tay cam de xoa ben Cloudinary, khong phai du lieu cong khai")
                .doesNotContain(PUBLIC_ID_ANH)
                .doesNotContain("cloudinaryPublicId");

        // 5. Admin an — bai bien mat khoi duong cong khai VA khoi tong hop.
        anBai(maDanhGia);
        khangDinhTongHop(0, 0.0);
        JsonNode trangKhiDaAn = docTrangCongKhai();
        assertThat(trangKhiDaAn.get("content").size()).isZero();
        assertThat(trangKhiDaAn.toString())
                .as("bai da an khong duoc lo ra qua bat ky truong nao, ke ca URL anh")
                .doesNotContain("\"maDanhGia\":" + maDanhGia)
                .doesNotContain(URL_ANH);
        assertThat(trangKhiDaAn.get("tongSo").asLong()).isZero();

        // 6. Hien lai — tong hop phai quay ve dung gia tri cu, khong phai mot con so moi.
        hienBai(maDanhGia);
        khangDinhTongHop(1, 5.0);
        assertThat(docTrangCongKhai().toString()).contains("\"maDanhGia\":" + maDanhGia);

        // 7. Go binh chon: bam lan hai la go, va van khong cham tong hop.
        assertThat(binhChon(maDanhGia).getBody()).contains("\"soLuotHuuIch\":0");
        khangDinhTongHop(1, 5.0);

        // 8. Chu so huu go anh cua minh — asset ben Cloudinary phai duoc don theo.
        ResponseEntity<String> xoaAnh = rest.postForEntity("/api/danh-gia/hinh-anh/{id}/xoa",
                new HttpEntity<>(bearer(chuSoHuu)), String.class, maHinhAnh);
        assertThat(xoaAnh.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(hinhAnhRepository.countByMaDanhGia(maDanhGia)).isZero();
        verify(cloudinaryService).deleteByPublicId(PUBLIC_ID_ANH);
        khangDinhTongHop(1, 5.0);

        // 9. Tac gia tu xoa bai — hop le, va tong hop tro ve rong.
        ResponseEntity<String> tuXoa = rest.postForEntity("/api/danh-gia/xoa-danh-gia/{id}",
                new HttpEntity<>(bearer(chuSoHuu)), String.class, maDanhGia);
        assertThat(tuXoa.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(suDanhGiaRepository.findById(maDanhGia)).isEmpty();
        khangDinhTongHop(0, 0.0);

        // 10. Dau kiem duyet phai song sot qua ca chuoi tren, khong chi qua mot lan xoa.
        ResponseEntity<String> dangLai = dangBaiTho(5, "Bai dang lai sau ca chuoi thao tac");
        assertThat(dangLai.getStatusCode().value()).isEqualTo(403);
        assertThat(dangLai.getBody() == null ? "" : dangLai.getBody())
                .contains("\"code\":\"FORBIDDEN\"");
        khangDinhTongHop(0, 0.0);
    }

    // ---------------------------------------------------------------------

    /** So sanh gia tri ghi san voi ket qua tinh truc tiep tu cac dong HIEN_THI that. */
    private void khangDinhTongHop(int soLuotMongDoi, double trungBinhMongDoi) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            List<SuDanhGia> hienThi = suDanhGiaRepository.findAll().stream()
                    .filter(d -> d.getSach() != null && d.getSach().getMaSach() == maSach)
                    .filter(d -> d.getTrangThai() == TrangThaiDanhGia.HIEN_THI)
                    .toList();
            double trungBinhThucTe = hienThi.isEmpty() ? 0
                    : hienThi.stream().mapToDouble(SuDanhGia::getDiemXepHang).average().orElse(0);

            Sach sach = sachRepository.findById((long) maSach).orElseThrow();
            assertThat(hienThi).hasSize(soLuotMongDoi);
            assertThat(sach.getSoLuotDanhGia())
                    .as("so luot ghi san phai bang so dong HIEN_THI that")
                    .isEqualTo(soLuotMongDoi);
            assertThat(sach.getTrungBinhXepHang())
                    .as("diem ghi san phai bang trung binh tinh lai")
                    .isEqualTo(trungBinhThucTe, org.assertj.core.data.Offset.offset(0.0001))
                    .isEqualTo(trungBinhMongDoi, org.assertj.core.data.Offset.offset(0.0001));
        });
    }

    private JsonNode docTrangCongKhai() throws IOException {
        String body = rest.getForObject(
                "/api/danh-gia?maSach={maSach}&size=50", String.class, maSach);
        return mapper.readTree(body == null ? "{}" : body);
    }

    private long dangBai(int diem) {
        ResponseEntity<String> response = dangBaiTho(diem, "Bai goc cua chuoi bat bien");
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("fixture: bai dau tien phai dang duoc")
                .isTrue();
        return suDanhGiaRepository.findAll().stream()
                .filter(d -> d.getSach() != null && d.getSach().getMaSach() == maSach)
                .map(SuDanhGia::getMaDanhGia)
                .findFirst()
                .orElseThrow(() -> new AssertionError("khong tim thay danh gia vua tao"));
    }

    private ResponseEntity<String> dangBaiTho(int diem, String nhanXet) {
        HttpHeaders headers = bearer(chuSoHuu);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity("/api/danh-gia/them-danh-gia-v1",
                new HttpEntity<>("{\"maSach\":" + maSach + ",\"diemXepHang\":" + diem
                        + ",\"nhanXet\":\"" + nhanXet + "\"}", headers), String.class);
    }

    private void suaBai(long maDanhGia, int diem, String nhanXet) {
        HttpHeaders headers = bearer(chuSoHuu);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity("/api/danh-gia/sua-danh-gia/{id}",
                new HttpEntity<>("{\"diemXepHang\":" + diem + ",\"nhanXet\":\"" + nhanXet + "\"}", headers),
                String.class, maDanhGia);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("chu so huu sua duoc bai cua minh")
                .isTrue();
    }

    private ResponseEntity<String> binhChon(long maDanhGia) {
        return rest.postForEntity("/api/danh-gia/{id}/huu-ich",
                new HttpEntity<>(bearer(nguoiBinhChon)), String.class, maDanhGia);
    }

    private long taiAnh(long maDanhGia, String idempotencyKey) throws IOException {
        HttpHeaders headers = bearer(chuSoHuu);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Idempotency-Key", idempotencyKey);

        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.IMAGE_PNG);
        ByteArrayResource resource = new ByteArrayResource(PNG) {
            @Override
            public String getFilename() {
                return "invariant.png";
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("tep", new HttpEntity<>(resource, partHeaders));

        ResponseEntity<String> response = rest.postForEntity("/api/danh-gia/{id}/hinh-anh",
                new HttpEntity<>(body, headers), String.class, maDanhGia);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .as("phan hoi upload cung khong duoc lo public id")
                .doesNotContain(PUBLIC_ID_ANH);
        return mapper.readTree(response.getBody()).get("maHinhAnh").asLong();
    }

    private void anBai(long maDanhGia) {
        doiTrangThaiQuaAdmin("unactive", maDanhGia);
    }

    private void hienBai(long maDanhGia) {
        doiTrangThaiQuaAdmin("active", maDanhGia);
    }

    private void doiTrangThaiQuaAdmin(String hanhDong, long maDanhGia) {
        ResponseEntity<String> response = rest.exchange("/api/admin/danh-gia/{hanhDong}/{id}",
                HttpMethod.POST, new HttpEntity<>(bearer(quanTri)), String.class,
                hanhDong, maDanhGia);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("admin %s danh gia", hanhDong)
                .isTrue();
    }

    private HttpHeaders bearer(String tenDangNhap) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity("/tai-khoan/dang-nhap",
                new HttpEntity<>("{\"username\":\"" + tenDangNhap
                        + "\",\"password\":\"" + MAT_KHAU + "\"}", headers), String.class);
        Matcher matcher = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")
                .matcher(response.getBody() == null ? "" : response.getBody());
        assertThat(matcher.find()).as("dang nhap %s thanh cong", tenDangNhap).isTrue();

        HttpHeaders bearerHeaders = new HttpHeaders();
        bearerHeaders.setBearerAuth(matcher.group());
        return bearerHeaders;
    }

    /** Sach rieng cho moi lan chay: cac cuon seed da co danh gia nen moi con so tuyet doi se sai. */
    private int taoSach() {
        return new TransactionTemplate(txManager).execute(status -> {
            Sach mau = sachRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("can it nhat mot cuon sach seed"));
            Sach sach = new Sach();
            sach.setTenSach("Review Invariant Fixture Book " + System.nanoTime());
            sach.setTenTacGia("Fixture");
            sach.setMoTa("Sach fixture cho ReviewInvariantIT");
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
            user.setHoDem("Review");
            user.setTen("Invariant");
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
