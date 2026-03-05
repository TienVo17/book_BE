package com.example.book_be.dao;

import com.example.book_be.entity.Sach;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RepositoryRestResource(path = "sach")
public interface SachRepository extends JpaRepository<Sach, Long>, JpaSpecificationExecutor {

    @Query("SELECT s FROM Sach s WHERE s.tenSach LIKE %:tenSach% OR s.tenTacGia LIKE %:tenSach%")
    Page<Sach> findByTenSachContaining(@RequestParam("tensach") String tenSach, Pageable pageable);

    Page<Sach> findByListTheLoai_MaTheLoai(@RequestParam("maTheLoai") int maTheLoai, Pageable pageable);

    Page<Sach> findByTenSachContainingAndListTheLoai_MaTheLoai(
            @RequestParam("tensach") String tenSach,
            @RequestParam("maTheLoai") int maTheLoai,
            Pageable pageable);

    // Best sellers - ordered by total sold quantity
    @Query("SELECT s FROM Sach s JOIN s.listChiTietDonHang ct WHERE s.isActive = 1 GROUP BY s ORDER BY SUM(ct.soLuong) DESC")
    List<Sach> findBanChay(Pageable pageable);

    // Newest books by ID desc
    List<Sach> findByIsActiveOrderByMaSachDesc(Integer isActive, Pageable pageable);

    // Slug lookups
    Sach findBySlug(String slug);
    boolean existsBySlug(String slug);

    // All active books (for sitemap)
    List<Sach> findAllByIsActive(Integer isActive);

    // Related books by shared categories
    @Query("SELECT s FROM Sach s JOIN s.listTheLoai t WHERE t.maTheLoai IN :maTheLoais AND s.maSach != :maSach AND s.isActive = 1")
    List<Sach> findLienQuan(@Param("maTheLoais") List<Integer> maTheLoais, @Param("maSach") int maSach, Pageable pageable);
}
