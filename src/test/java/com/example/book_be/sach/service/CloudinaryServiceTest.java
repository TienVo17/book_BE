package com.example.book_be.sach.service;

import com.cloudinary.Cloudinary;
import com.example.book_be.shared.web.StorageNotConfiguredException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudinaryServiceTest {

    @Test
    void noi_dung_png_that_di_qua_magic_bytes_du_content_type_khong_dang_tin() {
        CloudinaryService service = new CloudinaryService();
        ReflectionTestUtils.setField(service, "cloudinary", (Cloudinary) null);
        MockMultipartFile tep = new MockMultipartFile("tep", "anh.bin", "application/octet-stream",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0});

        assertThatThrownBy(() -> service.upload(tep, CloudinaryService.REVIEW_IMAGE_FOLDER))
                .isInstanceOf(StorageNotConfiguredException.class)
                .hasMessageContaining("CLOUDINARY_URL");
    }

    @Test
    void upload_multipart_chi_doc_noi_dung_mot_lan() {
        CloudinaryService service = new CloudinaryService();
        ReflectionTestUtils.setField(service, "cloudinary", (Cloudinary) null);
        MockMultipartFile tep = mock(MockMultipartFile.class);
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        when(tep.getSize()).thenReturn((long) png.length);
        when(tep.getOriginalFilename()).thenReturn("anh.png");
        try {
            when(tep.getBytes()).thenReturn(png);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }

        assertThatThrownBy(() -> service.upload(tep, CloudinaryService.REVIEW_IMAGE_FOLDER))
                .isInstanceOf(StorageNotConfiguredException.class);

        try {
            verify(tep).getBytes();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void file_gia_danh_jpg_bi_chan_bang_noi_dung_that() {
        CloudinaryService service = new CloudinaryService();
        MockMultipartFile tep = new MockMultipartFile("tep", "gia.jpg", "image/jpeg",
                "%PDF-1.7 fake executable".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> service.upload(tep, CloudinaryService.REVIEW_IMAGE_FOLDER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JPEG, PNG hoac WebP");
    }

    @Test
    void prefix_png_bon_byte_khong_du_chu_ky_bi_chan() {
        CloudinaryService service = new CloudinaryService();
        MockMultipartFile tep = new MockMultipartFile("tep", "gia.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0, 0, 0, 0, 0, 0, 0, 0});

        assertThatThrownBy(() -> service.upload(tep, CloudinaryService.REVIEW_IMAGE_FOLDER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JPEG, PNG hoac WebP");
    }

    @Test
    void webp_thieu_chunk_anh_hop_le_bi_chan() {
        CloudinaryService service = new CloudinaryService();
        MockMultipartFile tep = new MockMultipartFile("tep", "gia.webp", "image/webp",
                new byte[]{0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50,
                        0x4A, 0x55, 0x4E, 0x4B});

        assertThatThrownBy(() -> service.upload(tep, CloudinaryService.REVIEW_IMAGE_FOLDER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JPEG, PNG hoac WebP");
    }

    @Test
    void xoa_public_id_khi_thieu_cau_hinh_phai_nem_loi() {
        CloudinaryService service = new CloudinaryService();
        ReflectionTestUtils.setField(service, "cloudinary", (Cloudinary) null);

        assertThatThrownBy(() -> service.deleteByPublicId("reviews/abc"))
                .isInstanceOf(StorageNotConfiguredException.class)
                .hasMessageContaining("CLOUDINARY_URL");
    }
}
