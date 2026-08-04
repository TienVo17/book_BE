package com.example.book_be.giamgia.web;

import com.example.book_be.giamgia.service.CouponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/coupon")
public class CouponController {

    @Autowired
    private CouponService couponService;

    @PostMapping("/kiem-tra")
    public ResponseEntity<?> kiemTra(@RequestBody Map<String, Object> body) {
        String ma = body.get("ma") instanceof String value ? value : null;
        Object tongTienRaw = body.get("tongTien");
        if (tongTienRaw == null) {
            tongTienRaw = body.get("tongGioHang");
        }
        if (ma == null || ma.isBlank() || tongTienRaw == null) {
            throw new IllegalArgumentException("Yêu cầu coupon không hợp lệ.");
        }
        double tongTien = Double.parseDouble(String.valueOf(tongTienRaw));
        Map<String, Object> result = couponService.kiemTra(ma, tongTien);
        return ResponseEntity.ok(result);
    }
}
