package com.example.book_be.sach.service;

import com.cloudinary.Cloudinary;
import com.example.book_be.shared.web.StorageNotConfiguredException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for uploading and deleting book images in Cloudinary.
 */
@Service
public class CloudinaryService {

    public static final String BOOK_IMAGE_FOLDER = "web-ban-sach/books";
    /** Anh do nguoi dung tai len KHONG tron vao khong gian anh catalog. */
    public static final String REVIEW_IMAGE_FOLDER = "web-ban-sach/reviews";
    private static final String BIEN_MOI_TRUONG = "CLOUDINARY_URL";
    private static final long MAX_IMAGE_SIZE_BYTES = 10L * 1024 * 1024;

    @Autowired(required = false)
    private Cloudinary cloudinary;

    public CloudinaryUploadResult upload(MultipartFile file) throws IOException {
        return upload(file, BOOK_IMAGE_FOLDER);
    }

    /**
     * Tham so hoa thu muc de anh nguoi dung khong nam chung khong gian voi anh catalog.
     * Khi co su co, cau hoi "doi tuong nao do nguoi dung tai len" phai tra loi duoc.
     */
    public CloudinaryUploadResult upload(MultipartFile file, String thuMuc) throws IOException {
        validateImage(file.getSize(), file.getOriginalFilename());
        return upload(
                file.getBytes(),
                file.getContentType(),
                file.getOriginalFilename(),
                thuMuc);
    }

    /**
     * Upload noi dung da duoc caller doc de tinh hash ma khong nhan doi mang byte tren heap.
     * Validation van nam tai ranh gioi Cloudinary, khong day niem tin vao caller.
     */
    public CloudinaryUploadResult upload(byte[] bytes, String contentType,
                                         String originalFilename, String thuMuc) throws IOException {
        validateImage(bytes == null ? 0 : bytes.length, originalFilename);
        kiemTraNoiDungThatLaAnh(bytes);
        return uploadBytes(bytes, contentType, originalFilename, thuMuc);
    }

    public CloudinaryUploadResult uploadBase64Image(String dataUri, String fileNamePrefix) throws IOException {
        if (dataUri == null || !dataUri.startsWith("data:image/")) {
            throw new IllegalArgumentException("Chuoi anh base64 khong hop le");
        }

        int separatorIndex = dataUri.indexOf(',');
        if (separatorIndex < 0) {
            throw new IllegalArgumentException("Chuoi anh base64 khong hop le");
        }

        String metadata = dataUri.substring(5, separatorIndex);
        String base64Payload = dataUri.substring(separatorIndex + 1);
        String mimeType = metadata.split(";")[0];
        byte[] bytes = Base64.getDecoder().decode(base64Payload);
        String extension = mimeType.contains("/") ? mimeType.substring(mimeType.indexOf('/') + 1) : "jpg";
        String originalFilename = fileNamePrefix + "-" + UUID.randomUUID() + "." + extension;

        validateImage(bytes.length, originalFilename);
        kiemTraNoiDungThatLaAnh(bytes);
        return uploadBytes(bytes, mimeType, originalFilename, BOOK_IMAGE_FOLDER);
    }

    /**
     * Nem loi khi chua cau hinh, KHONG im lang tra ve.
     *
     * <p>Ban truoc tra ve ngay khi {@code cloudinary == null}. Tren stack Docker mac dinh
     * (CLOUDINARY_URL rong), moi test "xoa anh" se xanh ma khong he goi ra ngoai lan nao —
     * mot test gia cho dung bat bien no tuyen bo bao ve.
     */
    public void deleteByPublicId(String publicId) throws IOException {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        if (cloudinary == null) {
            throw new StorageNotConfiguredException(BIEN_MOI_TRUONG);
        }
        cloudinary.uploader().destroy(publicId, Map.of("resource_type", "image"));
    }

    public boolean isConfigured() {
        return cloudinary != null;
    }

    private CloudinaryUploadResult uploadBytes(byte[] bytes, String contentType, String originalFilename,
                                               String thuMuc) throws IOException {
        if (cloudinary == null) {
            throw new StorageNotConfiguredException(BIEN_MOI_TRUONG);
        }

        Map<String, Object> options = new HashMap<>();
        options.put("folder", thuMuc);
        options.put("resource_type", "image");
        options.put("public_id", buildPublicId(originalFilename));
        @SuppressWarnings("rawtypes")
        Map uploadResult = cloudinary.uploader().upload(bytes, options);
        return new CloudinaryUploadResult(
                (String) uploadResult.get("secure_url"),
                (String) uploadResult.get("public_id")
        );
    }

    /**
     * Kiem tra NOI DUNG THAT, dat BEN TRONG service nay de moi caller deu thua huong.
     *
     * <p>Dat o mot service goi len tren day thi ranh gioi tin cay khong he dich chuyen:
     * {@code validateImage} van chi doc Content-Type do client tu khai, va mot tep bat ky
     * doi duoi thanh .jpg voi header gia van di qua.
     *
     * <p>Day la BO LOC DINH DANG, khong phai bao dam an toan. No chan nham lan va thu
     * doi duoi tep; no khong chung minh anh vo hai.
     */
    private void kiemTraNoiDungThatLaAnh(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            throw new IllegalArgumentException("Tep khong phai anh hop le");
        }
        boolean jpeg = (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
        boolean png = (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A;
        boolean webpContainer = bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
                && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50;
        boolean webpChunk = bytes.length >= 16 && bytes[12] == 0x56 && bytes[13] == 0x50 && bytes[14] == 0x38
                && (bytes[15] == 0x20 || bytes[15] == 0x4C || bytes[15] == 0x58);
        boolean webp = webpContainer && webpChunk;
        if (!jpeg && !png && !webp) {
            throw new IllegalArgumentException("Chi chap nhan anh JPEG, PNG hoac WebP");
        }
    }

    private void validateImage(long fileSize, String fileName) {
        if (fileSize > MAX_IMAGE_SIZE_BYTES) {
            throw new IllegalArgumentException("File " + fileName + " vuot qua 10MB");
        }
    }

    private String buildPublicId(String originalFilename) {
        String sanitized = originalFilename == null ? "book-image" : originalFilename
                .replaceAll("[^a-zA-Z0-9-_\\.]", "-")
                .replaceAll("-+", "-");
        return sanitized + "-" + UUID.randomUUID();
    }
}
