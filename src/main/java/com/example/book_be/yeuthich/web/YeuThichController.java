package com.example.book_be.yeuthich.web;

import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.sach.repository.SachRepository;
import com.example.book_be.yeuthich.repository.SachYeuThichRepository;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.yeuthich.domain.SachYeuThich;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
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
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập.");
        }
        List<SachYeuThich> list = sachYeuThichRepository.findByNguoiDung_MaNguoiDung(user.getMaNguoiDung());
        return ResponseEntity.ok(list);
    }

    // POST /api/yeu-thich/{maSach} - add book to wishlist
    @PostMapping("/{maSach}")
    public ResponseEntity<?> addToWishlist(@PathVariable int maSach) {
        NguoiDung user = getCurrentUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập.");
        }

        // Check if already in wishlist
        if (sachYeuThichRepository.existsByNguoiDung_MaNguoiDungAndSach_MaSach(user.getMaNguoiDung(), maSach)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sách đã có trong danh sách yêu thích.");
        }

        Sach sach = sachRepository.findById((long) maSach)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sách không tồn tại."));

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
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập.");
        }

        if (!sachYeuThichRepository.existsByNguoiDung_MaNguoiDungAndSach_MaSach(user.getMaNguoiDung(), maSach)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sách không có trong danh sách yêu thích.");
        }

        sachYeuThichRepository.deleteByNguoiDung_MaNguoiDungAndSach_MaSach(user.getMaNguoiDung(), maSach);
        return ResponseEntity.ok().body("Đã xóa khỏi danh sách yêu thích");
    }
}
