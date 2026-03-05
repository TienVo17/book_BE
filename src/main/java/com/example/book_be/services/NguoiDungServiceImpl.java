package com.example.book_be.services;

import com.example.book_be.dao.NguoiDungRepository;
import com.example.book_be.entity.NguoiDung;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NguoiDungServiceImpl implements NguoiDungService {

    @Autowired
    private NguoiDungRepository nguoiDungRepository;

    @Override
    public NguoiDung getHoSo(String tenDangNhap) {
        return nguoiDungRepository.findByTenDangNhap(tenDangNhap);
    }

    @Override
    public NguoiDung capNhatHoSo(String tenDangNhap, NguoiDung updates) {
        NguoiDung nguoiDung = nguoiDungRepository.findByTenDangNhap(tenDangNhap);
        if (nguoiDung == null) {
            return null;
        }
        // Chỉ cập nhật các trường được phép, không cho thay đổi thông tin nhạy cảm
        if (updates.getHoDem() != null) {
            nguoiDung.setHoDem(updates.getHoDem());
        }
        if (updates.getTen() != null) {
            nguoiDung.setTen(updates.getTen());
        }
        if (updates.getSoDienThoai() != null) {
            nguoiDung.setSoDienThoai(updates.getSoDienThoai());
        }
        if (updates.getDiaChiMuaHang() != null) {
            nguoiDung.setDiaChiMuaHang(updates.getDiaChiMuaHang());
        }
        if (updates.getDiaChiGiaoHang() != null) {
            nguoiDung.setDiaChiGiaoHang(updates.getDiaChiGiaoHang());
        }
        // gioiTinh là char (primitive), luôn cập nhật nếu khác '\0'
        if (updates.getGioiTinh() != '\0') {
            nguoiDung.setGioiTinh(updates.getGioiTinh());
        }
        return nguoiDungRepository.save(nguoiDung);
    }
}
