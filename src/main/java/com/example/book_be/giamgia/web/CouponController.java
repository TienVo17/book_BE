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
        try {
            String ma = (String) body.get("ma");
            Object tongTienRaw = body.get("tongTien");
            if (tongTienRaw == null) {
                tongTienRaw = body.get("tongGioHang");
            }
            double tongTien = Double.parseDouble(String.valueOf(tongTienRaw));
            Map<String, Object> result = couponService.kiemTra(ma, tongTien);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            // Khong tra e.getMessage() ra client — tranh lo text exception/thong tin noi bo.
            return ResponseEntity.badRequest().body("Yêu cầu không hợp lệ");
        }
    }
}
