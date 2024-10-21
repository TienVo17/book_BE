package com.example.book_be.services;

import com.example.book_be.dao.NguoiDungRepository;
import com.example.book_be.entity.NguoiDung;
import com.example.book_be.entity.ThongBao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class TaiKhoanService {
    @Autowired
    private NguoiDungRepository nguoiDungRepository;

    public ResponseEntity<?> dangKyNguoiDung(NguoiDung nguoiDung) {
        // kiểm tra tên đăng nhập đã tồn tại chưa
        if(nguoiDungRepository.existsByTenDangNhap(nguoiDung.getTenDangNhap())) {
            return ResponseEntity.badRequest().body(new ThongBao("Tên đăng nhập đã tồn tại"));
        }
        if(nguoiDungRepository.existsByEmail(nguoiDung.getEmail())) {
            return ResponseEntity.badRequest().body(new ThongBao("Email đã tồn tại"));
        }
        // Neu khong co loi luu nguoi dung
        NguoiDung nguoiDung_DaDangKy =nguoiDungRepository.save(nguoiDung);
        return ResponseEntity.ok("Đăng ký thành công");
    }
 }
