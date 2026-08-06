package com.example.book_be.shared.web;

import com.example.book_be.danhgia.web.ReviewImageValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewImageErrorHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler(
            new ApiErrorWriter(new ObjectMapper()));

    @Test
    void validation_giu_nguyen_ma_loi_rieng() {
        var response = handler.handleReviewImage(
                new ReviewImageValidationException(
                        ReviewImageValidationException.Ma.REVIEW_IMAGE_TOO_LARGE,
                        "Mỗi ảnh tối đa 5MB."),
                request("/api/danh-gia/1/hinh-anh"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("REVIEW_IMAGE_TOO_LARGE");
    }

    @Test
    void multipart_vuot_nguong_tra_413_thay_vi_500() {
        var response = handler.handleUploadTooLarge(
                new MaxUploadSizeExceededException(10L * 1024 * 1024),
                request("/api/danh-gia/1/hinh-anh"));

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void thieu_part_tep_tra_400_thay_vi_500() {
        var response = handler.handleBadRequest(
                new MissingServletRequestPartException("tep"),
                request("/api/danh-gia/1/hinh-anh"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void multipart_hong_cu_phap_tra_400_co_ma_on_dinh() {
        var response = handler.handleMultipart(
                new MultipartException("broken multipart"),
                request("/api/danh-gia/1/hinh-anh"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_MULTIPART");
    }

    @Test
    void thieu_storage_tra_503_va_neu_dung_ten_bien() {
        var response = handler.handleStorageNotConfigured(
                new StorageNotConfiguredException("CLOUDINARY_URL"),
                request("/api/danh-gia/1/hinh-anh"));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("STORAGE_NOT_CONFIGURED");
        assertThat(response.getBody().message()).contains("CLOUDINARY_URL");
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        return request;
    }
}
