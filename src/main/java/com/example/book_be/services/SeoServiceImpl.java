package com.example.book_be.services;

import com.example.book_be.dao.HinhAnhRepository;
import com.example.book_be.dao.SachRepository;
import com.example.book_be.dao.TheLoaiRepository;
import com.example.book_be.entity.HinhAnh;
import com.example.book_be.entity.Sach;
import com.example.book_be.entity.TheLoai;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SeoServiceImpl implements SeoService {

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Autowired
    private SachRepository sachRepository;

    @Autowired
    private TheLoaiRepository theLoaiRepository;

    @Autowired
    private HinhAnhRepository hinhAnhRepository;

    @Override
    public Map<String, Object> getMetaTags(int maSach) {
        Sach sach = sachRepository.findById((long) maSach).orElse(null);
        Map<String, Object> meta = new HashMap<>();
        if (sach == null) {
            return meta;
        }

        String title = sach.getTenSach();
        String canonical = frontendUrl + "/sach/" + (sach.getSlug() != null ? sach.getSlug() : sach.getMaSach());

        // Description: truncate moTa to 160 chars
        String description = "";
        if (sach.getMoTa() != null && !sach.getMoTa().isEmpty()) {
            description = sach.getMoTa().length() > 160
                    ? sach.getMoTa().substring(0, 160)
                    : sach.getMoTa();
        }

        // OG image: load first image via repository (listHinhAnh is lazy)
        String ogImage = "";
        List<HinhAnh> images = hinhAnhRepository.findAll((root, query, builder) ->
                builder.equal(root.get("sach").get("maSach"), maSach)
        );
        if (!images.isEmpty()) {
            String url = images.get(0).getUrlHinh();
            ogImage = (url != null && (url.startsWith("http") || url.startsWith("data:image/"))) ? url : frontendUrl + url;
        }

        // JSON-LD Schema.org Book
        Map<String, Object> jsonLd = new HashMap<>();
        jsonLd.put("@context", "https://schema.org");
        jsonLd.put("@type", "Book");
        jsonLd.put("name", title);
        jsonLd.put("author", sach.getTenTacGia());
        jsonLd.put("isbn", sach.getISBN());
        jsonLd.put("description", description);
        jsonLd.put("url", canonical);
        if (!ogImage.isEmpty()) {
            jsonLd.put("image", ogImage);
        }
        if (sach.getGiaBan() > 0) {
            Map<String, Object> offer = new HashMap<>();
            offer.put("@type", "Offer");
            offer.put("price", sach.getGiaBan());
            offer.put("priceCurrency", "VND");
            jsonLd.put("offers", offer);
        }

        meta.put("title", title);
        meta.put("description", description);
        meta.put("canonical", canonical);
        meta.put("ogTitle", title);
        meta.put("ogDescription", description);
        meta.put("ogImage", ogImage);
        meta.put("ogUrl", canonical);
        meta.put("ogType", "book");
        meta.put("jsonLd", jsonLd);

        return meta;
    }

    @Override
    public String generateSitemap() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // Homepage
        sb.append("  <url>\n");
        sb.append("    <loc>").append(frontendUrl).append("/</loc>\n");
        sb.append("    <changefreq>daily</changefreq>\n");
        sb.append("    <priority>1.0</priority>\n");
        sb.append("  </url>\n");

        // All active books
        List<Sach> sachs = sachRepository.findAllByIsActive(1);
        for (Sach sach : sachs) {
            String path = sach.getSlug() != null
                    ? "/sach/" + sach.getSlug()
                    : "/sach/" + sach.getMaSach();
            sb.append("  <url>\n");
            sb.append("    <loc>").append(frontendUrl).append(path).append("</loc>\n");
            sb.append("    <changefreq>weekly</changefreq>\n");
            sb.append("    <priority>0.8</priority>\n");
            sb.append("  </url>\n");
        }

        // Public categories with active books only
        List<Object[]> categories = theLoaiRepository.findAllWithBookCount();
        for (Object[] cat : categories) {
            Integer maTheLoai = ((Number) cat[0]).intValue();
            if (theLoaiRepository.countActiveSachByMaTheLoai(maTheLoai) <= 0) {
                continue;
            }
            String slug = (String) cat[2];
            if (slug == null || slug.isBlank()) {
                continue;
            }
            sb.append("  <url>\n");
            sb.append("    <loc>").append(frontendUrl).append("/the-loai/").append(slug).append("</loc>\n");
            sb.append("    <changefreq>weekly</changefreq>\n");
            sb.append("    <priority>0.6</priority>\n");
            sb.append("  </url>\n");
        }

        sb.append("</urlset>");
        return sb.toString();
    }
}

