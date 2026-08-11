package com.example.book_be.yeuthich.repository;

import com.example.book_be.yeuthich.domain.SachYeuThich;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

/** exported=false: wishlist la du lieu rieng tu nguoi dung; /api/yeu-thich/** la contract duy nhat. */
@RepositoryRestResource(path = "sach-yeu-thich", exported = false)
public interface SachYeuThichRepository extends JpaRepository<SachYeuThich, Long> {

    @Query("SELECT DISTINCT w FROM SachYeuThich w "
            + "JOIN FETCH w.sach s LEFT JOIN FETCH s.listHinhAnh "
            + "WHERE w.nguoiDung.maNguoiDung = :maNguoiDung "
            + "ORDER BY w.maSachYeuThich")
    List<SachYeuThich> findWishlistSnapshot(@Param("maNguoiDung") int maNguoiDung);

    List<SachYeuThich> findByNguoiDung_MaNguoiDung(int maNguoiDung);

    boolean existsByNguoiDung_MaNguoiDungAndSach_MaSach(int maNguoiDung, int maSach);

    @Modifying(flushAutomatically = true)
    long deleteByNguoiDung_MaNguoiDungAndSach_MaSach(int maNguoiDung, int maSach);
}
