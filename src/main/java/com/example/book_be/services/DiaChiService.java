package com.example.book_be.services;

import com.example.book_be.entity.DiaChiGiaoHang;

import java.util.List;

public interface DiaChiService {
    List<DiaChiGiaoHang> findByNguoiDung(int maNguoiDung);
    DiaChiGiaoHang save(int maNguoiDung, DiaChiGiaoHang diaChi);
    DiaChiGiaoHang update(int maNguoiDung, int maDiaChi, DiaChiGiaoHang diaChi);
    void delete(int maNguoiDung, int maDiaChi);
}
