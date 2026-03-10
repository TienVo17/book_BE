package com.example.book_be.controller;

import com.example.book_be.dto.theloai.TheLoaiResponse;
import com.example.book_be.services.TheLoaiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "http://localhost:3000/")
@RequestMapping("/api/the-loai")
public class TheLoaiController {

    @Autowired
    private TheLoaiService theLoaiService;

    @GetMapping
    public ResponseEntity<List<TheLoaiResponse>> findAll() {
        return ResponseEntity.ok(theLoaiService.getDanhSachTheLoaiPublic());
    }

    @GetMapping("/{slug}")
    public ResponseEntity<?> findBySlug(@PathVariable String slug) {
        try {
            return ResponseEntity.ok(theLoaiService.getTheLoaiPublicBySlug(slug));
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getReason()));
        }
    }
}
