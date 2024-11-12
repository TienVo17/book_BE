package com.example.book_be.controller;

import com.example.book_be.entity.SuDanhGia;
import com.example.book_be.services.review.DanhGiaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/danh-gia")
@CrossOrigin(origins = "http://localhost:3000")
public class DanhGiaController {
    @Autowired
    private DanhGiaService danhGiaService;
    @PostMapping("/them-danh-gia")
    public SuDanhGia addReview(@RequestBody SuDanhGia danhGia) {
        if (danhGia.getNguoiDung() == null || danhGia.getNguoiDung().getMaNguoiDung() == 0) {
            throw new IllegalArgumentException("MaNguoiDung không hợp lệ hoặc không tồn tại.");
        }

        System.out.println("MaNguoiDung nhận được: " + danhGia.getNguoiDung().getMaNguoiDung());

        return danhGiaService.addReview(
                danhGia.getNhanXet(),
                danhGia.getDiemXepHang(),
                (long) danhGia.getNguoiDung().getMaNguoiDung(),
                (long) danhGia.getSach().getMaSach()
        );
    }


}
