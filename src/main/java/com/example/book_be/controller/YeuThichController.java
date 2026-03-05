package com.example.book_be.controller;

import com.example.book_be.dao.NguoiDungRepository;
import com.example.book_be.dao.SachRepository;
import com.example.book_be.dao.SachYeuThichRepository;
import com.example.book_be.entity.NguoiDung;
import com.example.book_be.entity.Sach;
import com.example.book_be.entity.SachYeuThich;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:3000/")
@RequestMapping("/api/yeu-thich")
public class YeuThichController {

    @Autowired
    private SachYeuThichRepository sachYeuThichRepository;

    @Autowired
    private NguoiDungRepository nguoiDungRepository;

    @Autowired
    private SachRepository sachRepository;

    // Resolve current authenticated user; returns null if not logged in
    private NguoiDung getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        return nguoiDungRepository.findByTenDangNhap(auth.getName());
    }

    // GET /api/yeu-thich - list wishlist items for current user
    @GetMapping
    public ResponseEntity<?> findAll() {
        NguoiDung user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body("Chưa đăng nhập");
        }
        List<SachYeuThich> list = sachYeuThichRepository.findByNguoiDung_MaNguoiDung(user.getMaNguoiDung());
        return ResponseEntity.ok(list);
    }

    // POST /api/yeu-thich/{maSach} - add book to wishlist
    @PostMapping("/{maSach}")
    public ResponseEntity<?> addToWishlist(@PathVariable int maSach) {
        NguoiDung user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body("Chưa đăng nhập");
        }

        // Check if already in wishlist
        if (sachYeuThichRepository.existsByNguoiDung_MaNguoiDungAndSach_MaSach(user.getMaNguoiDung(), maSach)) {
            return ResponseEntity.badRequest().body("Sách đã có trong danh sách yêu thích");
        }

        Sach sach = sachRepository.findById((long) maSach).orElse(null);
        if (sach == null) {
            return ResponseEntity.notFound().build();
        }

        SachYeuThich yeuThich = new SachYeuThich();
        yeuThich.setNguoiDung(user);
        yeuThich.setSach(sach);
        return ResponseEntity.ok(sachYeuThichRepository.save(yeuThich));
    }

    // DELETE /api/yeu-thich/{maSach} - remove book from wishlist
    @DeleteMapping("/{maSach}")
    @Transactional
    public ResponseEntity<?> removeFromWishlist(@PathVariable int maSach) {
        NguoiDung user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body("Chưa đăng nhập");
        }

        if (!sachYeuThichRepository.existsByNguoiDung_MaNguoiDungAndSach_MaSach(user.getMaNguoiDung(), maSach)) {
            return ResponseEntity.notFound().build();
        }

        sachYeuThichRepository.deleteByNguoiDung_MaNguoiDungAndSach_MaSach(user.getMaNguoiDung(), maSach);
        return ResponseEntity.ok().body("Đã xóa khỏi danh sách yêu thích");
    }
}
