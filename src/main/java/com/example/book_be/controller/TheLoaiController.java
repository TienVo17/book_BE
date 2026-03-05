package com.example.book_be.controller;

import com.example.book_be.dao.TheLoaiRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "http://localhost:3000/")
@RequestMapping("/api/the-loai")
public class TheLoaiController {

    @Autowired
    private TheLoaiRepository theLoaiRepository;

    // GET /api/the-loai - list all categories with book count
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> findAll() {
        List<Object[]> rows = theLoaiRepository.findAllWithBookCount();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> item = new HashMap<>();
            item.put("maTheLoai", row[0]);
            item.put("tenTheLoai", row[1]);
            item.put("soLuongSach", row[2]);
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }
}
