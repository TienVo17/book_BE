package com.example.book_be.services;

import com.cloudinary.Cloudinary;
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

    private static final String BOOK_IMAGE_FOLDER = "web-ban-sach/books";
    private static final long MAX_IMAGE_SIZE_BYTES = 10L * 1024 * 1024;

    @Autowired(required = false)
    private Cloudinary cloudinary;

    public CloudinaryUploadResult upload(MultipartFile file) throws IOException {
        validateImage(file.getContentType(), file.getSize(), file.getOriginalFilename());
        return uploadBytes(file.getBytes(), file.getContentType(), file.getOriginalFilename());
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

        validateImage(mimeType, bytes.length, originalFilename);
        return uploadBytes(bytes, mimeType, originalFilename);
    }

    public void deleteByPublicId(String publicId) throws IOException {
        if (cloudinary == null || publicId == null || publicId.isBlank()) {
            return;
        }
        cloudinary.uploader().destroy(publicId, Map.of("resource_type", "image"));
    }

    public boolean isConfigured() {
        return cloudinary != null;
    }

    private CloudinaryUploadResult uploadBytes(byte[] bytes, String contentType, String originalFilename) throws IOException {
        if (cloudinary == null) {
            throw new IllegalStateException("Cloudinary chua duoc cau hinh. Set CLOUDINARY_URL env var.");
        }

        Map<String, Object> options = new HashMap<>();
        options.put("folder", BOOK_IMAGE_FOLDER);
        options.put("resource_type", "image");
        options.put("public_id", buildPublicId(originalFilename));
        @SuppressWarnings("rawtypes")
        Map uploadResult = cloudinary.uploader().upload(bytes, options);
        return new CloudinaryUploadResult(
                (String) uploadResult.get("secure_url"),
                (String) uploadResult.get("public_id")
        );
    }

    private void validateImage(String contentType, long fileSize, String fileName) {
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Chi chap nhan file anh");
        }
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
