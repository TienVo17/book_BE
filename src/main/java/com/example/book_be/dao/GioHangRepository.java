package com.example.book_be.dao;

import com.example.book_be.entity.GioHang;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.Optional;

@RepositoryRestResource(path = "gio-hang", exported = false)
public interface GioHangRepository extends JpaRepository<GioHang, Integer> {
    @Query("SELECT DISTINCT c FROM GioHang c LEFT JOIN FETCH c.sach s LEFT JOIN FETCH s.listHinhAnh WHERE c.nguoiDung.maNguoiDung = :maNguoiDung")
    List<GioHang> findByMaNguoiDung(@Param("maNguoiDung") int maNguoiDung);

    @Query("SELECT DISTINCT c FROM GioHang c LEFT JOIN FETCH c.sach s LEFT JOIN FETCH s.listHinhAnh WHERE c.nguoiDung.maNguoiDung = :maNguoiDung AND c.sach.maSach = :maSach")
    Optional<GioHang> findByMaNguoiDungAndMaSach(@Param("maNguoiDung") int maNguoiDung, @Param("maSach") int maSach);

    @Modifying
    @Transactional
    @Query("DELETE FROM GioHang c WHERE c.nguoiDung.maNguoiDung = :maNguoiDung")
    void deleteGioHangByMaNguoiDung(@Param("maNguoiDung") int maNguoiDung);

    @Modifying
    @Transactional
    @Query("DELETE FROM GioHang c WHERE c.nguoiDung.maNguoiDung = :maNguoiDung AND c.sach.maSach = :maSach")
    void deleteByMaNguoiDungAndMaSach(@Param("maNguoiDung") int maNguoiDung, @Param("maSach") int maSach);
}
