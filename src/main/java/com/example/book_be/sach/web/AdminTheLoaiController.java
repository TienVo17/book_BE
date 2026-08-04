package com.example.book_be.sach.web;

import com.example.book_be.sach.dto.TheLoaiAdminUpsertRequest;
import com.example.book_be.sach.service.TheLoaiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/the-loai")
public class AdminTheLoaiController {

    @Autowired
    private TheLoaiService theLoaiService;

    @GetMapping
    public ResponseEntity<?> findAll() {
        return ResponseEntity.ok(theLoaiService.getDanhSachTheLoaiAdmin());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody TheLoaiAdminUpsertRequest request) {
        return ResponseEntity.ok(theLoaiService.taoTheLoai(request));
    }

    @PutMapping("/{maTheLoai}")
    public ResponseEntity<?> update(@PathVariable Integer maTheLoai, @RequestBody TheLoaiAdminUpsertRequest request) {
        return ResponseEntity.ok(theLoaiService.capNhatTheLoai(maTheLoai, request));
    }

    @DeleteMapping("/{maTheLoai}")
    public ResponseEntity<?> delete(@PathVariable Integer maTheLoai) {
        theLoaiService.xoaTheLoai(maTheLoai);
        return ResponseEntity.ok(Map.of("message", "Xóa thể loại thành công"));
    }
}
