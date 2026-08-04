package com.example.book_be.nguoidung.web;

import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.domain.DiaChiGiaoHang;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.service.DiaChiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
        NguoiDung user = requireCurrentUser();
        List<DiaChiGiaoHang> list = diaChiService.findByNguoiDung(user.getMaNguoiDung());
        return ResponseEntity.ok(list);
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestBody DiaChiGiaoHang diaChi) {
        NguoiDung user = requireCurrentUser();
        DiaChiGiaoHang saved = diaChiService.save(user.getMaNguoiDung(), diaChi);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable int id, @RequestBody DiaChiGiaoHang diaChi) {
        NguoiDung user = requireCurrentUser();
        DiaChiGiaoHang updated = diaChiService.update(user.getMaNguoiDung(), id, diaChi);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable int id) {
        NguoiDung user = requireCurrentUser();
        diaChiService.delete(user.getMaNguoiDung(), id);
        return ResponseEntity.ok("Xóa địa chỉ thành công");
    }

    private NguoiDung requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Chưa đăng nhập.");
        }
        NguoiDung user = nguoiDungRepository.findByTenDangNhap(authentication.getName());
        if (user == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Chưa đăng nhập.");
        }
        return user;
    }
}
