package com.example.book_be.controller;

import com.example.book_be.dao.NguoiDungRepository;
import com.example.book_be.entity.DiaChiGiaoHang;
import com.example.book_be.entity.NguoiDung;
import com.example.book_be.services.DiaChiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dia-chi")
public class DiaChiController {

    @Autowired
    private DiaChiService diaChiService;

    @Autowired
    private NguoiDungRepository nguoiDungRepository;

    @GetMapping
    public ResponseEntity<?> findAll() {
        try {
            NguoiDung user = getCurrentUser();
            if (user == null) {
                return ResponseEntity.status(401).body("Chưa đăng nhập");
            }
            List<DiaChiGiaoHang> list = diaChiService.findByNguoiDung(user.getMaNguoiDung());
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestBody DiaChiGiaoHang diaChi) {
        try {
            NguoiDung user = getCurrentUser();
            if (user == null) {
                return ResponseEntity.status(401).body("Chưa đăng nhập");
            }
            DiaChiGiaoHang saved = diaChiService.save(user.getMaNguoiDung(), diaChi);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable int id, @RequestBody DiaChiGiaoHang diaChi) {
        try {
            NguoiDung user = getCurrentUser();
            if (user == null) {
                return ResponseEntity.status(401).body("Chưa đăng nhập");
            }
            DiaChiGiaoHang updated = diaChiService.update(user.getMaNguoiDung(), id, diaChi);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable int id) {
        try {
            NguoiDung user = getCurrentUser();
            if (user == null) {
                return ResponseEntity.status(401).body("Chưa đăng nhập");
            }
            diaChiService.delete(user.getMaNguoiDung(), id);
            return ResponseEntity.ok("Xóa địa chỉ thành công");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private NguoiDung getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        return nguoiDungRepository.findByTenDangNhap(authentication.getName());
    }
}
