package com.example.book_be.controller;

import com.example.book_be.services.CouponService;
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
        try {
            String ma = (String) body.get("ma");
            double tongTien = Double.parseDouble(String.valueOf(body.get("tongTien")));
            Map<String, Object> result = couponService.kiemTra(ma, tongTien);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
