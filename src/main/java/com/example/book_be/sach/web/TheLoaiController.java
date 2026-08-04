package com.example.book_be.sach.web;

import com.example.book_be.sach.dto.TheLoaiResponse;
import com.example.book_be.sach.service.TheLoaiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
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
        return ResponseEntity.ok(theLoaiService.getTheLoaiPublicBySlug(slug));
    }
}
