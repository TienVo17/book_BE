package com.example.book_be.controller;

import com.example.book_be.services.SeoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@CrossOrigin(origins = "http://localhost:3000/")
@RequestMapping("/api/seo")
public class SeoController {

    @Autowired
    private SeoService seoService;

    // GET /api/seo/sach/{id} - return meta tags JSON for a book
    @GetMapping("/sach/{id}")
    public ResponseEntity<Map<String, Object>> getMetaTags(@PathVariable int id) {
        Map<String, Object> meta = seoService.getMetaTags(id);
        if (meta.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(meta);
    }
}
