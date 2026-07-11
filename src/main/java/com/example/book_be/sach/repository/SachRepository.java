package com.example.book_be.sach.repository;

import com.example.book_be.sach.domain.Sach;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Tru kho nguyen tu: chi tru khi con du hang. Tra ve so ban ghi cap nhat (1 = thanh cong, 0 = het hang).
     * Chong oversell/TOCTOU ma khong can lock thu cong. Caller phai @Transactional.
     */
    @Modifying
    @Query("UPDATE Sach s SET s.soLuong = s.soLuong - :soLuong WHERE s.maSach = :maSach AND s.soLuong >= :soLuong")
    int truKhoNeuDu(@Param("maSach") int maSach, @Param("soLuong") int soLuong);

    /** Hoan kho khi huy don. Caller phai @Transactional. */
    @Modifying
    @Query("UPDATE Sach s SET s.soLuong = s.soLuong + :soLuong WHERE s.maSach = :maSach")
    int hoanKho(@Param("maSach") int maSach, @Param("soLuong") int soLuong);
}
