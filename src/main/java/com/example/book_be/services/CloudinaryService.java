package com.example.book_be.services;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for uploading images to Cloudinary CDN.
 * Gracefully handles missing Cloudinary configuration.
 */
@Service
public class CloudinaryService {

    @Autowired(required = false)
    private Cloudinary cloudinary;

    /**
     * Upload a multipart image file to Cloudinary.
     */
    public String upload(MultipartFile file) throws IOException {
        if (cloudinary == null) {
            throw new IllegalStateException("Cloudinary chua duoc cau hinh. Set CLOUDINARY_URL env var.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Chi chap nhan file anh");
        }
        Map<String, Object> options = new HashMap<>();
        options.put("folder", "web-ban-sach/books");
        options.put("resource_type", "image");
        @SuppressWarnings("rawtypes")
        Map uploadResult = cloudinary.uploader().upload(file.getBytes(), options);
        return (String) uploadResult.get("secure_url");
    }

    /**
     * Check if Cloudinary is properly configured.
     */
    public boolean isConfigured() {
        return cloudinary != null;
    }
}
