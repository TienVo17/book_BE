package com.example.book_be.donhang.repository;

import com.example.book_be.donhang.domain.LichSuTrangThaiDonHang;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Bang audit noi bo - khong expose ra REST (theo tien le GioHangRepository). */
@Repository
@RepositoryRestResource(exported = false)
public interface LichSuTrangThaiDonHangRepository extends JpaRepository<LichSuTrangThaiDonHang, Long> {

    List<LichSuTrangThaiDonHang> findByMaDonHangOrderByThoiDiemAsc(int maDonHang);
}
