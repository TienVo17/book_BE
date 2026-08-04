package com.example.book_be.giamgia.web.admin;

import com.example.book_be.giamgia.domain.Coupon;
import com.example.book_be.giamgia.service.CouponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/coupon")
public class CouponAdminController {

    @Autowired
    private CouponService couponService;

    @GetMapping
    public ResponseEntity<List<Coupon>> findAll() {
        return ResponseEntity.ok(couponService.findAll());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Coupon coupon) {
        Coupon saved = couponService.save(coupon);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable int id, @RequestBody Coupon coupon) {
        Coupon updated = couponService.update(id, coupon);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable int id) {
        couponService.delete(id);
        return ResponseEntity.ok("Xóa coupon thành công");
    }
}
