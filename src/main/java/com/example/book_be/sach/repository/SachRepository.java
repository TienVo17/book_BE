package com.example.book_be.sach.repository;

import com.example.book_be.sach.domain.Sach;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;

import java.util.List;
import java.util.Optional;

/**
 * Repository nay van duoc Spring Data REST export (path "sach") de {@code GET /sach/{id}}
 * tiep tuc doc duoc — xac nhan boi ReviewExposureIT. Nhung MOI query method rieng ben duoi
 * phai mang {@code @RestResource(exported = false)}: neu khong, no bi phoi cong khai duoi
 * {@code /sach/search/<tenPhuongThuc>} ma KHONG di qua bo loc isActive/quyen han cua tang
 * controller — vi du {@code /sach/search/findAllByIsActive?isActive=0} se liet ke toan bo
 * sach admin da an, va cac cau @Modifying doi ton kho cung goi duoc tu ben ngoai.
 */
@RepositoryRestResource(path = "sach")
public interface SachRepository extends JpaRepository<Sach, Long>, JpaSpecificationExecutor {

    // Best sellers - ordered by total sold quantity
    @RestResource(exported = false)
    @Query("SELECT s FROM Sach s JOIN s.listChiTietDonHang ct WHERE s.isActive = 1 GROUP BY s ORDER BY SUM(ct.soLuong) DESC")
    List<Sach> findBanChay(Pageable pageable);

    // Newest books by ID desc
    @RestResource(exported = false)
    List<Sach> findByIsActiveOrderByMaSachDesc(Integer isActive, Pageable pageable);

    // Slug lookups
    @RestResource(exported = false)
    Sach findBySlug(String slug);
    @RestResource(exported = false)
    boolean existsBySlug(String slug);

    // All active books (for sitemap)
    @RestResource(exported = false)
    List<Sach> findAllByIsActive(Integer isActive);

    // Related books by shared categories
    @RestResource(exported = false)
    @Query("SELECT s FROM Sach s JOIN s.listTheLoai t WHERE t.maTheLoai IN :maTheLoais AND s.maSach != :maSach AND s.isActive = 1")
    List<Sach> findLienQuan(@Param("maTheLoais") List<Integer> maTheLoais, @Param("maSach") int maSach, Pageable pageable);

    /**
     * Goi y khi go tim kiem: chi sach dang active, khop ten sach hoac ten tac gia.
     *
     * <p>Hai ve REPLACE(REPLACE(...)) o duoi chuan hoa rieng "Đ"/"đ" -> "D"/"d": day la MOT CHU
     * CAI RIENG trong bang chu cai tieng Viet nen collation MySQL KHONG tu gop no voi "D/d" nhu
     * cac dau khac (da do bang request that: go "dang gia" khong khop "Đáng Giá" neu thieu ve
     * nay, du cac dau thanh/dau mu khac collation da tu bo qua). LIKE %...% von da khong dung
     * duoc index va bang sach rat nho nen them ve OR nay khong dang ngai hieu nang.
     */
    @RestResource(exported = false)
    @Query("SELECT s FROM Sach s WHERE s.isActive = 1 "
            + "AND (LOWER(s.tenSach) LIKE LOWER(CONCAT('%', :tuKhoa, '%')) "
            + "OR LOWER(s.tenTacGia) LIKE LOWER(CONCAT('%', :tuKhoa, '%')) "
            + "OR LOWER(REPLACE(REPLACE(s.tenSach, 'Đ', 'D'), 'đ', 'd')) "
            + "LIKE LOWER(REPLACE(REPLACE(CONCAT('%', :tuKhoa, '%'), 'Đ', 'D'), 'đ', 'd')) "
            + "OR LOWER(REPLACE(REPLACE(s.tenTacGia, 'Đ', 'D'), 'đ', 'd')) "
            + "LIKE LOWER(REPLACE(REPLACE(CONCAT('%', :tuKhoa, '%'), 'Đ', 'D'), 'đ', 'd'))) "
            + "ORDER BY s.maSach DESC")
    List<Sach> findGoiY(@Param("tuKhoa") String tuKhoa, Pageable pageable);

    /**
     * Tru kho nguyen tu: chi tru khi con du hang. Tra ve so ban ghi cap nhat (1 = thanh cong, 0 = het hang).
     * Chong oversell/TOCTOU ma khong can lock thu cong. Caller phai @Transactional.
     */
    @RestResource(exported = false)
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Sach s SET s.soLuong = s.soLuong - :soLuong "
            + "WHERE s.maSach = :maSach AND :soLuong > 0 AND s.soLuong >= :soLuong "
            + "AND (s.isActive IS NULL OR s.isActive = 1)")
    int truKhoNeuDu(@Param("maSach") int maSach, @Param("soLuong") int soLuong);

    /** Hoan kho khi huy don, chi khi ket qua khong vuot qua Integer.MAX_VALUE. */
    @RestResource(exported = false)
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Sach s SET s.soLuong = s.soLuong + :soLuong "
            + "WHERE s.maSach = :maSach AND :soLuong > 0 AND s.soLuong <= :maxBefore")
    int hoanKho(@Param("maSach") int maSach, @Param("soLuong") int soLuong,
                @Param("maxBefore") int maxBefore);

    @RestResource(exported = false)
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Sach s SET s.soLuong = s.soLuong + :soLuong "
            + "WHERE s.maSach = :maSach AND :soLuong > 0 AND s.soLuong <= :maxBefore")
    int tangTonKhoNeuKhongVuotQua(@Param("maSach") int maSach, @Param("soLuong") int soLuong,
                                  @Param("maxBefore") int maxBefore);

    @RestResource(exported = false)
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Sach s SET s.soLuong = s.soLuong - :soLuong "
            + "WHERE s.maSach = :maSach AND :soLuong > 0 AND s.soLuong >= :soLuong")
    int giamTonKhoNeuDu(@Param("maSach") int maSach, @Param("soLuong") int soLuong);

    @RestResource(exported = false)
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
    @RestResource(exported = false)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sach s WHERE s.maSach = :maSach")
    Optional<Sach> khoaSachDeCapNhat(@Param("maSach") int maSach);

    @RestResource(exported = false)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sach s WHERE s.maSach = :maSach")
    Optional<Sach> findByIdForCartWrite(@Param("maSach") int maSach);
}
