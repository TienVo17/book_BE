package com.example.book_be.danhgia.service;

import com.example.book_be.danhgia.domain.DanhGiaHinhAnh;
import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.danhgia.repository.DanhGiaHinhAnhRepository;
import com.example.book_be.danhgia.repository.SuDanhGiaRepository;
import com.example.book_be.danhgia.web.ReviewImageValidationException;
import com.example.book_be.sach.service.CloudinaryService;
import com.example.book_be.sach.service.CloudinaryUploadResult;
import com.example.book_be.shared.security.RateLimiter;
import com.example.book_be.shared.web.StorageNotConfiguredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DanhGiaHinhAnhServiceTest {

    private DanhGiaHinhAnhRepository hinhAnhRepository;
    private SuDanhGiaRepository suDanhGiaRepository;
    private CloudinaryService cloudinaryService;
    private RateLimiter rateLimiter;
    private DanhGiaHinhAnhService service;

    @BeforeEach
    void setUp() {
        hinhAnhRepository = mock(DanhGiaHinhAnhRepository.class);
        suDanhGiaRepository = mock(SuDanhGiaRepository.class);
        cloudinaryService = mock(CloudinaryService.class);
        rateLimiter = mock(RateLimiter.class);
        service = new DanhGiaHinhAnhService(
                hinhAnhRepository, suDanhGiaRepository, cloudinaryService, rateLimiter);

        when(rateLimiter.choPhep(any(), anyInt(), any())).thenReturn(true);
        SuDanhGia danhGia = new SuDanhGia();
        danhGia.setMaNguoiDung(7);
        when(suDanhGiaRepository.khoaDeThemAnh(11L)).thenReturn(Optional.of(danhGia));
        when(hinhAnhRepository.tangHanNgachNeuCon(7, DanhGiaHinhAnhService.HAN_NGACH_TRON_DOI))
                .thenReturn(1);
    }

    @Test
    void thieu_idempotency_key_bi_tu_choi_truoc_khi_khoa_review() throws IOException {
        assertThatThrownBy(() -> service.themAnh(11L, 7, null, png()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        loi -> assertThat(loi.getStatusCode().value()).isEqualTo(400));
        verify(suDanhGiaRepository, never()).khoaDeThemAnh(anyLong());
        verify(cloudinaryService, never()).upload(
                any(byte[].class), any(), any(), any());
    }

    @Test
    void idempotency_key_ngoai_allow_list_bi_tu_choi() throws IOException {
        assertThatThrownBy(() -> service.themAnh(11L, 7, "upload key", png()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        loi -> assertThat(loi.getStatusCode().value()).isEqualTo(400));
        verify(suDanhGiaRepository, never()).khoaDeThemAnh(anyLong());
        verify(cloudinaryService, never()).upload(
                any(byte[].class), any(), any(), any());
    }

    @Test
    void tep_rong_co_ma_loi_rieng_va_khong_goi_cloudinary() throws IOException {
        MockMultipartFile tep = new MockMultipartFile("tep", "rong.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.themAnh(11L, 7, "upload-key", tep))
                .isInstanceOfSatisfying(ReviewImageValidationException.class,
                        loi -> assertThat(loi.getMa()).isEqualTo(
                                ReviewImageValidationException.Ma.REVIEW_IMAGE_EMPTY));
        verify(cloudinaryService, never()).upload(
                any(byte[].class), any(), any(), any());
    }

    @Test
    void qua_5mb_bi_tu_choi_truoc_khi_doc_byte_va_goi_cloudinary() throws IOException {
        MockMultipartFile tep = mock(MockMultipartFile.class);
        when(tep.isEmpty()).thenReturn(false);
        when(tep.getSize()).thenReturn(DanhGiaHinhAnhService.KICH_THUOC_TOI_DA_BYTES + 1);

        assertThatThrownBy(() -> service.themAnh(11L, 7, "upload-key", tep))
                .isInstanceOfSatisfying(ReviewImageValidationException.class,
                        loi -> assertThat(loi.getMa()).isEqualTo(
                                ReviewImageValidationException.Ma.REVIEW_IMAGE_TOO_LARGE));
        verify(tep, never()).getBytes();
        verify(cloudinaryService, never()).upload(
                any(byte[].class), any(), any(), any());
    }

    @Test
    void anh_thu_sau_co_ma_too_many() throws IOException {
        when(hinhAnhRepository.countByMaDanhGia(11L)).thenReturn(5L);

        assertThatThrownBy(() -> service.themAnh(11L, 7, "upload-key", png()))
                .isInstanceOfSatisfying(ReviewImageValidationException.class,
                        loi -> assertThat(loi.getMa()).isEqualTo(
                                ReviewImageValidationException.Ma.REVIEW_IMAGE_TOO_MANY));
        verify(cloudinaryService, never()).upload(
                any(byte[].class), any(), any(), any());
    }

    @Test
    void het_han_ngach_tron_doi_co_ma_rieng() throws IOException {
        when(hinhAnhRepository.tangHanNgachNeuCon(7, DanhGiaHinhAnhService.HAN_NGACH_TRON_DOI))
                .thenReturn(0);

        assertThatThrownBy(() -> service.themAnh(11L, 7, "upload-key", png()))
                .isInstanceOfSatisfying(ReviewImageValidationException.class,
                        loi -> assertThat(loi.getMa()).isEqualTo(
                                ReviewImageValidationException.Ma.REVIEW_IMAGE_QUOTA_EXCEEDED));
        verify(cloudinaryService, never()).upload(
                any(byte[].class), any(), any(), any());
    }

    @Test
    void nguoi_khac_khong_duoc_them_anh() throws IOException {
        assertThatThrownBy(() -> service.themAnh(11L, 8, "upload-key", png()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        loi -> assertThat(loi.getStatusCode().value()).isEqualTo(403));
        verify(cloudinaryService, never()).upload(
                any(byte[].class), any(), any(), any());
    }

    @Test
    void dinh_dang_sai_duoc_doi_sang_ma_unsupported() throws IOException {
        when(cloudinaryService.upload(
                any(byte[].class), any(), any(), eq(CloudinaryService.REVIEW_IMAGE_FOLDER)))
                .thenThrow(new IllegalArgumentException("khong phai anh"));

        assertThatThrownBy(() -> service.themAnh(11L, 7, "upload-key", png()))
                .isInstanceOfSatisfying(ReviewImageValidationException.class,
                        loi -> assertThat(loi.getMa()).isEqualTo(
                                ReviewImageValidationException.Ma.REVIEW_IMAGE_UNSUPPORTED_TYPE));
    }

    @Test
    void thieu_cloudinary_khong_bi_doi_thanh_validation_error() throws IOException {
        when(cloudinaryService.upload(
                any(byte[].class), any(), any(), eq(CloudinaryService.REVIEW_IMAGE_FOLDER)))
                .thenThrow(new StorageNotConfiguredException("CLOUDINARY_URL"));

        assertThatThrownBy(() -> service.themAnh(11L, 7, "upload-key", png()))
                .isInstanceOf(StorageNotConfiguredException.class)
                .hasMessageContaining("CLOUDINARY_URL");
    }

    @Test
    void gui_lai_cung_khoa_va_cung_tep_tra_anh_da_luu_khong_upload_lai() throws IOException {
        DanhGiaHinhAnh daLuu = anh(9, "reviews/already-uploaded");
        daLuu.setMaDanhGia(11L);
        daLuu.setIdempotencyKey("upload-key-1");
        daLuu.setNoiDungSha256(sha256(png().getBytes()));
        when(hinhAnhRepository.findByMaDanhGiaAndIdempotencyKey(11L, "upload-key-1"))
                .thenReturn(Optional.of(daLuu));

        DanhGiaHinhAnh ketQua = service.themAnh(11L, 7, "upload-key-1", png());

        assertThat(ketQua).isSameAs(daLuu);
        verify(cloudinaryService, never()).upload(
                any(byte[].class), any(), any(), any());
        verify(hinhAnhRepository, never()).tangHanNgachNeuCon(anyInt(), anyInt());
    }

    @Test
    void dung_lai_khoa_cho_tep_khac_bi_tu_choi_khong_upload() throws IOException {
        DanhGiaHinhAnh daLuu = anh(9, "reviews/already-uploaded");
        daLuu.setMaDanhGia(11L);
        daLuu.setIdempotencyKey("upload-key-1");
        daLuu.setNoiDungSha256(sha256("tep-cu".getBytes(StandardCharsets.UTF_8)));
        when(hinhAnhRepository.findByMaDanhGiaAndIdempotencyKey(11L, "upload-key-1"))
                .thenReturn(Optional.of(daLuu));

        assertThatThrownBy(() -> service.themAnh(11L, 7, "upload-key-1", png()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        loi -> assertThat(loi.getStatusCode().value()).isEqualTo(409));
        verify(cloudinaryService, never()).upload(
                any(byte[].class), any(), any(), any());
    }

    @Test
    void db_save_loi_thi_xoa_upload_vua_tao() throws IOException {
        when(cloudinaryService.upload(
                any(byte[].class), any(), any(), eq(CloudinaryService.REVIEW_IMAGE_FOLDER)))
                .thenReturn(new CloudinaryUploadResult("https://cdn.test/anh.png", "reviews/public-id"));
        when(hinhAnhRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("db fail"));

        assertThatThrownBy(() -> service.themAnh(11L, 7, "upload-key", png()))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(cloudinaryService).deleteByPublicId("reviews/public-id");
    }

    @Test
    void xoa_anh_cua_chu_goi_cloudinary_truoc_khi_xoa_dong() throws IOException {
        DanhGiaHinhAnh anh = anh(1, "reviews/a");
        anh.setMaDanhGia(11L);
        SuDanhGia danhGia = new SuDanhGia();
        danhGia.setMaNguoiDung(7);
        when(hinhAnhRepository.findById(1L)).thenReturn(Optional.of(anh));
        when(suDanhGiaRepository.findById(11L)).thenReturn(Optional.of(danhGia));

        service.xoaAnh(1L, 7, false);

        var thuTu = inOrder(cloudinaryService, hinhAnhRepository);
        thuTu.verify(cloudinaryService).deleteByPublicId("reviews/a");
        thuTu.verify(hinhAnhRepository).delete(anh);
    }

    @Test
    void cloudinary_xoa_loi_thi_giu_dong_anh_de_retry() throws IOException {
        DanhGiaHinhAnh anh = anh(1, "reviews/a");
        when(hinhAnhRepository.findById(1L)).thenReturn(Optional.of(anh));
        doThrow(new IOException("cloudinary down"))
                .when(cloudinaryService).deleteByPublicId("reviews/a");

        assertThatThrownBy(() -> service.xoaAnh(1L, 7, true))
                .isInstanceOf(IOException.class);
        verify(hinhAnhRepository, never()).delete(any());
    }

    @Test
    void xoa_review_goi_cloudinary_cho_tung_anh_truoc_khi_xoa_dong() throws IOException {
        DanhGiaHinhAnh a = anh(1, "reviews/a");
        DanhGiaHinhAnh b = anh(2, "reviews/b");
        when(hinhAnhRepository.findByMaDanhGiaOrderByThuTuAsc(11L)).thenReturn(List.of(a, b));

        assertThat(service.donAnhCuaDanhGia(11L)).isEqualTo(2);

        verify(cloudinaryService).deleteByPublicId("reviews/a");
        verify(cloudinaryService).deleteByPublicId("reviews/b");
        verify(hinhAnhRepository).deleteAll(List.of(a, b));
    }

    @Test
    void cloudinary_loi_giua_danh_sach_thi_db_van_giu_tat_ca_tay_cam() throws IOException {
        DanhGiaHinhAnh a = anh(1, "reviews/a");
        DanhGiaHinhAnh b = anh(2, "reviews/b");
        when(hinhAnhRepository.findByMaDanhGiaOrderByThuTuAsc(11L)).thenReturn(List.of(a, b));
        doThrow(new IOException("cloudinary down"))
                .when(cloudinaryService).deleteByPublicId("reviews/b");

        assertThatThrownBy(() -> service.donAnhCuaDanhGia(11L))
                .isInstanceOf(IOException.class);
        verify(hinhAnhRepository, never()).deleteAll(any());
    }

    private MockMultipartFile png() {
        return new MockMultipartFile("tep", "anh.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0});
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private DanhGiaHinhAnh anh(long id, String publicId) {
        DanhGiaHinhAnh anh = new DanhGiaHinhAnh();
        anh.setMaHinhAnh(id);
        anh.setCloudinaryPublicId(publicId);
        return anh;
    }
}
