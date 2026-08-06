package com.example.book_be.sach.repository;

import com.example.book_be.sach.domain.Sach;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

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
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Sach s SET s.soLuong = s.soLuong - :soLuong "
            + "WHERE s.maSach = :maSach AND :soLuong > 0 AND s.soLuong >= :soLuong")
    int truKhoNeuDu(@Param("maSach") int maSach, @Param("soLuong") int soLuong);

    /** Hoan kho khi huy don, chi khi ket qua khong vuot qua Integer.MAX_VALUE. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Sach s SET s.soLuong = s.soLuong + :soLuong "
            + "WHERE s.maSach = :maSach AND :soLuong > 0 AND s.soLuong <= :maxBefore")
    int hoanKho(@Param("maSach") int maSach, @Param("soLuong") int soLuong,
                @Param("maxBefore") int maxBefore);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Sach s SET s.soLuong = s.soLuong + :soLuong "
            + "WHERE s.maSach = :maSach AND :soLuong > 0 AND s.soLuong <= :maxBefore")
    int tangTonKhoNeuKhongVuotQua(@Param("maSach") int maSach, @Param("soLuong") int soLuong,
                                  @Param("maxBefore") int maxBefore);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Sach s SET s.soLuong = s.soLuong - :soLuong "
            + "WHERE s.maSach = :maSach AND :soLuong > 0 AND s.soLuong >= :soLuong")
    int giamTonKhoNeuDu(@Param("maSach") int maSach, @Param("soLuong") int soLuong);

    @Query("SELECT s.soLuong FROM Sach s WHERE s.maSach = :maSach")
    Integer findSoLuongByMaSach(@Param("maSach") int maSach);

    /**
     * Chiem khoa ghi tren dong sach truoc khi doi danh gia cua no.
     *
     * <p>Cau tinh lai tong hop la {@code UPDATE sach SET ... = (SELECT ... FROM danhgia)}:
     * no ghi dong sach dong thoi doc cac dong danhgia. Hai transaction cung them danh gia cho
     * mot cuon sach se moi ben giu khoa INSERT tren danhgia roi cung doi khoa doc cheo nhau —
     * deadlock, da tai hien duoc bang DanhGiaAggregateIT.
     *
     * <p>Chiem khoa dong sach TRUOC moi thao tac danh gia tao ra mot thu tu khoa nhat quan,
     * nen khong con chu trinh cho doi. Caller phai @Transactional.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sach s WHERE s.maSach = :maSach")
    Optional<Sach> khoaSachDeCapNhat(@Param("maSach") int maSach);
}
