package com.example.book_be.services.admin;

import com.example.book_be.dao.HinhAnhRepository;
import com.example.book_be.entity.HinhAnh;
import com.example.book_be.entity.Sach;
import com.example.book_be.services.CloudinaryService;
import com.example.book_be.services.CloudinaryUploadResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BookImageStorageService {

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Autowired
    private HinhAnhRepository hinhAnhRepository;

    @Autowired(required = false)
    private CloudinaryService cloudinaryService;

    public void syncBookImages(Sach sach, List<String> imageSources) throws IOException {
        clearImagesByBook(sach);
        if (imageSources == null || imageSources.isEmpty()) {
            return;
        }

        int index = 1;
        for (String imageSource : imageSources) {
            if (imageSource == null || imageSource.isBlank()) {
                continue;
            }
            hinhAnhRepository.save(buildImageEntity(sach, imageSource.trim(), defaultImageName(sach, index++)));
        }
    }

    public List<HinhAnh> saveUploadedImages(Sach sach, MultipartFile[] files) throws IOException {
        List<HinhAnh> savedImages = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (cloudinaryService == null || !cloudinaryService.isConfigured()) {
                throw new IllegalStateException("Cloudinary chua duoc cau hinh. Set CLOUDINARY_URL env var.");
            }
            CloudinaryUploadResult result = cloudinaryService.upload(file);
            HinhAnh hinhAnh = new HinhAnh();
            hinhAnh.setTenHinhAnh(defaultUploadName(sach, file.getOriginalFilename()));
            hinhAnh.setUrlHinh(result.secureUrl());
            hinhAnh.setCloudinaryPublicId(result.publicId());
            hinhAnh.setIcon(false);
            hinhAnh.setSach(sach);
            hinhAnh.setDataImage(null);
            savedImages.add(hinhAnhRepository.save(hinhAnh));
        }
        return savedImages;
    }

    public Map<String, Object> migrateLegacyImages(int limit) throws IOException {
        if (cloudinaryService == null || !cloudinaryService.isConfigured()) {
            throw new IllegalStateException("Cloudinary chua duoc cau hinh. Set CLOUDINARY_URL env var.");
        }

        List<HinhAnh> legacyImages = hinhAnhRepository.findLegacyImages(PageRequest.of(0, Math.max(limit, 1)));
        List<Integer> migratedIds = new ArrayList<>();
        List<Integer> skippedIds = new ArrayList<>();

        for (HinhAnh legacyImage : legacyImages) {
            String dataUri = extractLegacyDataUri(legacyImage);
            if (dataUri == null) {
                skippedIds.add(legacyImage.getMaHinhAnh());
                continue;
            }

            CloudinaryUploadResult result = cloudinaryService.uploadBase64Image(
                    dataUri,
                    defaultImageName(legacyImage.getSach(), legacyImage.getMaHinhAnh())
            );
            legacyImage.setUrlHinh(result.secureUrl());
            legacyImage.setCloudinaryPublicId(result.publicId());
            legacyImage.setDataImage(null);
            hinhAnhRepository.save(legacyImage);
            migratedIds.add(legacyImage.getMaHinhAnh());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("requestedLimit", limit);
        response.put("processed", legacyImages.size());
        response.put("migrated", migratedIds.size());
        response.put("skipped", skippedIds.size());
        response.put("migratedIds", migratedIds);
        response.put("skippedIds", skippedIds);
        return response;
    }

    public void clearImagesByBook(Sach sach) throws IOException {
        if (sach == null) {
            return;
        }
        List<HinhAnh> existingImages = hinhAnhRepository.findAll((root, query, builder) ->
                builder.equal(root.get("sach").get("maSach"), sach.getMaSach())
        );
        for (HinhAnh existingImage : existingImages) {
            deleteManagedImage(existingImage);
        }
        if (!existingImages.isEmpty()) {
            hinhAnhRepository.deleteAll(existingImages);
        }
    }

    public String resolvePublicImageUrl(HinhAnh image) {
        if (image == null || image.getUrlHinh() == null || image.getUrlHinh().isBlank()) {
            return "";
        }
        String url = image.getUrlHinh();
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("data:image/")) {
            return url;
        }
        return frontendUrl + url;
    }

    private HinhAnh buildImageEntity(Sach sach, String imageSource, String imageName) throws IOException {
        HinhAnh hinhAnh = new HinhAnh();
        hinhAnh.setTenHinhAnh(imageName);
        hinhAnh.setIcon(false);
        hinhAnh.setSach(sach);

        if (imageSource.startsWith("data:image/")) {
            if (cloudinaryService == null || !cloudinaryService.isConfigured()) {
                throw new IllegalStateException("Cloudinary chua duoc cau hinh. Khong the luu anh base64 moi.");
            }
            CloudinaryUploadResult result = cloudinaryService.uploadBase64Image(imageSource, imageName);
            hinhAnh.setUrlHinh(result.secureUrl());
            hinhAnh.setCloudinaryPublicId(result.publicId());
            hinhAnh.setDataImage(null);
            return hinhAnh;
        }

        hinhAnh.setUrlHinh(imageSource);
        hinhAnh.setCloudinaryPublicId(null);
        hinhAnh.setDataImage(null);
        return hinhAnh;
    }

    private void deleteManagedImage(HinhAnh hinhAnh) throws IOException {
        if (hinhAnh == null || hinhAnh.getCloudinaryPublicId() == null || hinhAnh.getCloudinaryPublicId().isBlank()) {
            return;
        }
        if (cloudinaryService != null && cloudinaryService.isConfigured()) {
            cloudinaryService.deleteByPublicId(hinhAnh.getCloudinaryPublicId());
        }
    }

    private String extractLegacyDataUri(HinhAnh hinhAnh) {
        if (hinhAnh.getDataImage() != null && hinhAnh.getDataImage().startsWith("data:image/")) {
            return hinhAnh.getDataImage();
        }
        if (hinhAnh.getUrlHinh() != null && hinhAnh.getUrlHinh().startsWith("data:image/")) {
            return hinhAnh.getUrlHinh();
        }
        return null;
    }

    private String defaultImageName(Sach sach, int index) {
        String bookId = sach == null ? "unknown" : String.valueOf(sach.getMaSach());
        return "book-" + bookId + "-image-" + index;
    }

    private String defaultUploadName(Sach sach, String originalFilename) {
        String fallback = originalFilename == null || originalFilename.isBlank() ? "upload" : originalFilename;
        return defaultImageName(sach, 1) + "-" + fallback;
    }
}
