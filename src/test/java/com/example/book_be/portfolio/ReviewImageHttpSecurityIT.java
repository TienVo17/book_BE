package com.example.book_be.portfolio;

import com.example.book_be.TestcontainersConfig;
import com.example.book_be.danhgia.domain.DanhGiaHinhAnh;
import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.danhgia.domain.TrangThaiDanhGia;
import com.example.book_be.danhgia.repository.DanhGiaHinhAnhRepository;
import com.example.book_be.danhgia.repository.SuDanhGiaRepository;
import com.example.book_be.danhgia.service.DanhGiaHinhAnhService;
import com.example.book_be.danhgia.service.DanhGiaService;
import com.example.book_be.donhang.repository.DonHangRepository;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.domain.Quyen;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.repository.QuyenRepository;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.sach.repository.SachRepository;
import com.example.book_be.sach.service.CloudinaryService;
import com.example.book_be.sach.service.CloudinaryUploadResult;
import com.example.book_be.shared.web.StorageNotConfiguredException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.net.http.HttpClient;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "cloudinary.url=",
                "spring.servlet.multipart.max-file-size=1KB",
                "spring.servlet.multipart.max-request-size=2KB"
        })
@Import(TestcontainersConfig.class)
class ReviewImageHttpSecurityIT {

    private static final String MAT_KHAU = "ReviewImage@123";
    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
    };

    @Autowired TestRestTemplate rest;
    @Autowired NguoiDungRepository nguoiDungRepository;
    @Autowired QuyenRepository quyenRepository;
    @Autowired SuDanhGiaRepository suDanhGiaRepository;
    @Autowired DanhGiaHinhAnhRepository hinhAnhRepository;
    @Autowired SachRepository sachRepository;
    @Autowired DonHangRepository donHangRepository;
    @Autowired BCryptPasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager txManager;
    @Autowired DanhGiaHinhAnhService hinhAnhService;
    @Autowired DanhGiaService danhGiaService;

    @MockBean CloudinaryService cloudinaryService;
    @SpyBean SuDanhGiaRepository suDanhGiaRepositorySpy;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private String chuSoHuu;
    private String nguoiKhac;
    private String quanTri;
    private Long maDanhGia;
    private Integer maDonHang;
    private Integer maSach;

    @BeforeEach
    void provisionFixtures() throws IOException {
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory(httpClient));
        reset(cloudinaryService);
        when(cloudinaryService.upload(
                any(byte[].class), any(), any(), eq(CloudinaryService.REVIEW_IMAGE_FOLDER)))
                .thenReturn(new CloudinaryUploadResult(
                        "https://cdn.example.test/reviews/upload.png",
                        "web-ban-sach/reviews/upload"));

        long runId = System.nanoTime();
        chuSoHuu = taoNguoiDung("review-image-owner-" + runId, "USER");
        nguoiKhac = taoNguoiDung("review-image-other-" + runId, "USER");
        quanTri = taoNguoiDung("review-image-admin-" + runId, "ADMIN");
        maDanhGia = taoDanhGia(chuSoHuu);
    }

    @AfterEach
    void cleanupFixtures() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            if (maDanhGia != null) {
                hinhAnhRepository.deleteAll(hinhAnhRepository.findByMaDanhGiaOrderByThuTuAsc(maDanhGia));
                suDanhGiaRepository.findById(maDanhGia).ifPresent(suDanhGiaRepository::delete);
            }
        });
        DonHangDaGiaoFixture.xoaDon(txManager, donHangRepository, maDonHang);
        xoaNguoiDung(chuSoHuu);
        xoaNguoiDung(nguoiKhac);
        xoaNguoiDung(quanTri);
    }

    @Test
    void chu_so_huu_tai_anh_qua_http_va_khong_lo_public_id() throws IOException {
        ResponseEntity<String> response = themAnh(
                bearer(chuSoHuu), PNG, "anh.png", MediaType.IMAGE_PNG_VALUE, "upload-owner");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"urlHinh\":\"https://cdn.example.test/reviews/upload.png\"")
                .doesNotContain("cloudinaryPublicId")
                .doesNotContain("web-ban-sach/reviews/upload");
        List<DanhGiaHinhAnh> daLuu = hinhAnhRepository.findByMaDanhGiaOrderByThuTuAsc(maDanhGia);
        assertThat(daLuu).singleElement().satisfies(anh -> {
            assertThat(anh.getCloudinaryPublicId()).isEqualTo("web-ban-sach/reviews/upload");
            assertThat(anh.getIdempotencyKey()).isEqualTo("upload-owner");
            assertThat(anh.getNoiDungSha256()).hasSize(64);
            assertThat(anh.getThuTu()).isZero();
        });
        verify(cloudinaryService).upload(
                any(byte[].class), any(), any(), eq(CloudinaryService.REVIEW_IMAGE_FOLDER));
    }

    @Test
    void gui_lai_cung_khoa_va_tep_chi_upload_va_tinh_han_ngach_mot_lan() throws IOException {
        HttpHeaders chuSoHuuHeaders = bearer(chuSoHuu);
        ResponseEntity<String> lanDau = themAnh(
                chuSoHuuHeaders, PNG, "anh.png", MediaType.IMAGE_PNG_VALUE, "replay-key");
        int hanNgachSauLanDau = nguoiDungRepository.findByTenDangNhap(chuSoHuu)
                .getSoAnhDanhGiaDaDung();

        ResponseEntity<String> replay = themAnh(
                chuSoHuuHeaders, PNG, "anh.png", MediaType.IMAGE_PNG_VALUE, "replay-key");

        assertThat(lanDau.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody()).isEqualTo(lanDau.getBody());
        assertThat(hinhAnhRepository.countByMaDanhGia(maDanhGia)).isOne();
        assertThat(nguoiDungRepository.findByTenDangNhap(chuSoHuu)
                .getSoAnhDanhGiaDaDung()).isEqualTo(hanNgachSauLanDau);
        verify(cloudinaryService).upload(
                any(byte[].class), any(), any(), eq(CloudinaryService.REVIEW_IMAGE_FOLDER));
    }

    @Test
    void dung_lai_khoa_cho_tep_khac_tra_409_va_khong_upload_lai() throws IOException {
        HttpHeaders chuSoHuuHeaders = bearer(chuSoHuu);
        ResponseEntity<String> lanDau = themAnh(
                chuSoHuuHeaders, PNG, "anh.png", MediaType.IMAGE_PNG_VALUE, "conflict-key");

        byte[] pngKhac = PNG.clone();
        pngKhac[pngKhac.length - 1] = 1;
        ResponseEntity<String> conflict = themAnh(
                chuSoHuuHeaders, pngKhac, "anh-khac.png", MediaType.IMAGE_PNG_VALUE, "conflict-key");

        assertThat(lanDau.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertLoi(conflict, HttpStatus.CONFLICT, "CONFLICT");
        assertThat(hinhAnhRepository.countByMaDanhGia(maDanhGia)).isOne();
        verify(cloudinaryService).upload(
                any(byte[].class), any(), any(), eq(CloudinaryService.REVIEW_IMAGE_FOLDER));
    }

    @Test
    void thieu_hoac_sai_idempotency_key_tra_400_khong_upload() throws IOException {
        ResponseEntity<String> thieuKhoa = themAnh(
                bearer(chuSoHuu), PNG, "anh.png", MediaType.IMAGE_PNG_VALUE, null);
        ResponseEntity<String> saiKhoa = themAnh(
                bearer(chuSoHuu), PNG, "anh.png", MediaType.IMAGE_PNG_VALUE, "sai khoa");

        assertLoi(thieuKhoa, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertLoi(saiKhoa, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertThat(hinhAnhRepository.countByMaDanhGia(maDanhGia)).isZero();
        verify(cloudinaryService, never()).upload(
                any(byte[].class), any(), any(), any());
    }

    @Test
    void multipart_vuot_nguong_container_tra_413_truoc_cloudinary() throws IOException {
        byte[] quaLon = new byte[1500];
        System.arraycopy(PNG, 0, quaLon, 0, PNG.length);

        ResponseEntity<String> response = themAnh(
                bearer(chuSoHuu), quaLon, "qua-lon.png", MediaType.IMAGE_PNG_VALUE, "too-large");

        assertLoi(response, HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
        assertThat(hinhAnhRepository.countByMaDanhGia(maDanhGia)).isZero();
        verify(cloudinaryService, never()).upload(
                any(byte[].class), any(), any(), any());
    }

    @Test
    void nguoi_khac_khong_tai_anh_vao_review_cua_chu_so_huu() throws IOException {
        ResponseEntity<String> response = themAnh(bearer(nguoiKhac), PNG, "anh.png", MediaType.IMAGE_PNG_VALUE, "other-user");

        assertLoi(response, HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertThat(hinhAnhRepository.countByMaDanhGia(maDanhGia)).isZero();
        verify(cloudinaryService, never()).upload(
                any(byte[].class), any(), any(), any());
    }

    @Test
    void tep_rong_va_anh_thu_sau_co_ma_loi_http_rieng() throws IOException {
        ResponseEntity<String> tepRong = themAnh(bearer(chuSoHuu), new byte[0], "rong.png", MediaType.IMAGE_PNG_VALUE, "empty-file");
        assertLoi(tepRong, HttpStatus.BAD_REQUEST, "REVIEW_IMAGE_EMPTY");

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            for (int i = 0; i < 5; i++) {
                taoAnhTrucTiep(i, "existing-" + i);
            }
        });
        ResponseEntity<String> anhThuSau = themAnh(bearer(chuSoHuu), PNG, "anh.png", MediaType.IMAGE_PNG_VALUE, "image-upload");
        assertLoi(anhThuSau, HttpStatus.BAD_REQUEST, "REVIEW_IMAGE_TOO_MANY");
        assertThat(hinhAnhRepository.countByMaDanhGia(maDanhGia)).isEqualTo(5);
        verify(cloudinaryService, never()).upload(
                any(byte[].class), any(), any(), any());
    }

    @Test
    void thieu_storage_va_io_delete_loi_giu_nguyen_dong_anh() throws IOException {
        when(cloudinaryService.upload(
                any(byte[].class), any(), any(), eq(CloudinaryService.REVIEW_IMAGE_FOLDER)))
                .thenThrow(new StorageNotConfiguredException("CLOUDINARY_URL"));
        ResponseEntity<String> thieuStorage = themAnh(
                bearer(chuSoHuu), PNG, "anh.png", MediaType.IMAGE_PNG_VALUE, "storage-missing");
        assertLoi(thieuStorage, HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_NOT_CONFIGURED");

        Long maHinhAnh = new TransactionTemplate(txManager).execute(status ->
                taoAnhTrucTiep(0, "delete-fails").getMaHinhAnh());
        doThrow(new IOException("storage unavailable"))
                .when(cloudinaryService).deleteByPublicId("delete-fails");

        ResponseEntity<String> xoaLoi = xoaAnh(bearer(chuSoHuu), maHinhAnh);
        assertLoi(xoaLoi, HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_IO_ERROR");
        assertThat(hinhAnhRepository.findById(maHinhAnh)).isPresent();
    }

    @Test
    void upload_giu_review_lock_de_delete_cho_va_don_asset_sau_commit() throws Exception {
        NguoiDung chuReview = nguoiDungRepository.findByTenDangNhap(chuSoHuu);
        CountDownLatch uploadDaVaoCloudinary = new CountDownLatch(1);
        CountDownLatch deleteDaDenLock = new CountDownLatch(1);
        CountDownLatch choPhepUploadTraVe = new CountDownLatch(1);
        Answer<?> cauTraLoiMacDinh = org.mockito.Mockito.mockingDetails(
                suDanhGiaRepositorySpy)
                .getMockCreationSettings()
                .getDefaultAnswer();
        doAnswer(invocation -> {
            deleteDaDenLock.countDown();
            return cauTraLoiMacDinh.answer(invocation);
        }).when(suDanhGiaRepositorySpy).khoaDeXoaKemNguoiDung(maDanhGia);
        doAnswer(invocation -> {
            uploadDaVaoCloudinary.countDown();
            choLatch(choPhepUploadTraVe, "upload khong duoc release kip");
            return new CloudinaryUploadResult(
                    "https://cdn.example.test/reviews/race.png",
                    "web-ban-sach/reviews/race");
        }).when(cloudinaryService).upload(
                any(byte[].class), any(), any(), eq(CloudinaryService.REVIEW_IMAGE_FOLDER));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<DanhGiaHinhAnh> upload = pool.submit(() -> hinhAnhService.themAnh(
                    maDanhGia,
                    chuReview.getMaNguoiDung(),
                    "race-key",
                    new MockMultipartFile(
                            "tep", "race.png", MediaType.IMAGE_PNG_VALUE, PNG)));
            choLatch(uploadDaVaoCloudinary, "upload chua lay lock review kip");

            Future<SuDanhGia> delete = pool.submit(() -> danhGiaService.deleteReview(
                    maDanhGia, (long) chuReview.getMaNguoiDung(), false));
            choLatch(deleteDaDenLock, "delete chua den diem lay lock kip");

            assertThat(delete.isDone())
                    .as("delete phai cho pessimistic lock cua upload")
                    .isFalse();
            choPhepUploadTraVe.countDown();

            assertThat(upload.get(20, TimeUnit.SECONDS).getMaHinhAnh()).isPositive();
            assertThat(delete.get(20, TimeUnit.SECONDS).getMaDanhGia()).isEqualTo(maDanhGia);
            assertThat(suDanhGiaRepository.findById(maDanhGia)).isEmpty();
            assertThat(hinhAnhRepository.countByMaDanhGia(maDanhGia)).isZero();
            verify(cloudinaryService).deleteByPublicId("web-ban-sach/reviews/race");
        } finally {
            choPhepUploadTraVe.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void chu_so_huu_va_admin_duoc_xoa_con_nguoi_khac_bi_tu_choi() throws IOException {
        Long anhCuaChu = new TransactionTemplate(txManager).execute(status ->
                taoAnhTrucTiep(0, "owner-delete").getMaHinhAnh());

        ResponseEntity<String> biTuChoi = xoaAnh(bearer(nguoiKhac), anhCuaChu);
        assertLoi(biTuChoi, HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertThat(hinhAnhRepository.findById(anhCuaChu)).isPresent();

        ResponseEntity<String> chuXoa = xoaAnh(bearer(chuSoHuu), anhCuaChu);
        assertThat(chuXoa.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(hinhAnhRepository.findById(anhCuaChu)).isEmpty();
        verify(cloudinaryService).deleteByPublicId("owner-delete");

        Long anhViPham = new TransactionTemplate(txManager).execute(status ->
                taoAnhTrucTiep(0, "admin-delete").getMaHinhAnh());
        ResponseEntity<String> adminXoa = xoaAnh(bearer(quanTri), anhViPham);
        assertThat(adminXoa.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(hinhAnhRepository.findById(anhViPham)).isEmpty();
        verify(cloudinaryService).deleteByPublicId("admin-delete");
    }

    private void choLatch(CountDownLatch latch, String message) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message, e);
        }
    }

    private ResponseEntity<String> themAnh(HttpHeaders bearerHeaders, byte[] bytes,
                                           String tenTep, String contentType,
                                           String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(bearerHeaders);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }

        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(contentType));
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return tenTep;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("tep", new HttpEntity<>(resource, partHeaders));
        return rest.postForEntity("/api/danh-gia/{id}/hinh-anh",
                new HttpEntity<>(body, headers), String.class, maDanhGia);
    }

    private ResponseEntity<String> xoaAnh(HttpHeaders headers, long maHinhAnh) {
        return rest.postForEntity("/api/danh-gia/hinh-anh/{id}/xoa",
                new HttpEntity<>(headers), String.class, maHinhAnh);
    }

    private void assertLoi(ResponseEntity<String> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).contains("\"status\":" + status.value())
                .contains("\"code\":\"" + code + "\"")
                .contains("\"traceId\":");
    }

    private DanhGiaHinhAnh taoAnhTrucTiep(int thuTu, String publicId) {
        DanhGiaHinhAnh anh = new DanhGiaHinhAnh();
        anh.setMaDanhGia(maDanhGia);
        anh.setUrlHinh("https://cdn.example.test/" + publicId + ".png");
        anh.setCloudinaryPublicId(publicId);
        anh.setIdempotencyKey("fixture-" + publicId);
        anh.setNoiDungSha256("0".repeat(64));
        anh.setThuTu(thuTu);
        anh.setTaoLuc(new Timestamp(System.currentTimeMillis()));
        return hinhAnhRepository.saveAndFlush(anh);
    }

    private HttpHeaders bearer(String tenDangNhap) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.postForEntity("/tai-khoan/dang-nhap",
                new HttpEntity<>("{\"username\":\"" + tenDangNhap
                        + "\",\"password\":\"" + MAT_KHAU + "\"}", headers),
                String.class);
        Matcher matcher = Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")
                .matcher(response.getBody() == null ? "" : response.getBody());
        assertThat(matcher.find()).as("dang nhap %s thanh cong", tenDangNhap).isTrue();
        HttpHeaders bearerHeaders = new HttpHeaders();
        bearerHeaders.setBearerAuth(matcher.group());
        return bearerHeaders;
    }

    private Long taoDanhGia(String tenDangNhap) {
        return new TransactionTemplate(txManager).execute(status -> {
            NguoiDung nguoiDung = nguoiDungRepository.findByTenDangNhap(tenDangNhap);
            Sach sach = sachRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("can it nhat mot cuon sach seed"));
            maSach = sach.getMaSach();
            maDonHang = DonHangDaGiaoFixture.taoDonDaGiao(txManager, donHangRepository,
                    nguoiDungRepository, sachRepository, tenDangNhap, maSach);

            SuDanhGia danhGia = new SuDanhGia();
            danhGia.setNhanXet("Danh gia co anh");
            danhGia.setDiemXepHang(5F);
            danhGia.setTimestamp(new Timestamp(System.currentTimeMillis()));
            danhGia.setMaDonHang(maDonHang);
            danhGia.datTrangThai(TrangThaiDanhGia.HIEN_THI);
            danhGia.setNguoiDung(nguoiDung);
            danhGia.setSach(sach);
            return suDanhGiaRepository.saveAndFlush(danhGia).getMaDanhGia();
        });
    }

    private String taoNguoiDung(String tenDangNhap, String tenQuyen) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Quyen quyen = quyenRepository.findByTenQuyen(tenQuyen);
            assertThat(quyen).as("quyen %s da duoc seed", tenQuyen).isNotNull();
            NguoiDung user = new NguoiDung();
            user.setHoDem("Review");
            user.setTen("Image");
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
