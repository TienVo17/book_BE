package com.example.book_be.dao;

import com.example.book_be.entity.DiaChiGiaoHang;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiaChiGiaoHangRepository extends JpaRepository<DiaChiGiaoHang, Long> {
    List<DiaChiGiaoHang> findByNguoiDung_MaNguoiDung(int maNguoiDung);
}
