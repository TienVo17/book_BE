package com.example.book_be.danhgia.repository;

import com.example.book_be.danhgia.domain.DanhGiaHuuIch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Khong expose qua Spring Data REST. */
@RepositoryRestResource(exported = false)
public interface DanhGiaHuuIchRepository extends JpaRepository<DanhGiaHuuIch, Long> {

    Optional<DanhGiaHuuIch> findByMaDanhGiaAndMaNguoiDung(long maDanhGia, int maNguoiDung);

    /**
     * Dem cho CA trang trong mot truy van, thay vi mot truy van moi dong.
     *
     * <p>Day la ly do khong them cot {@code so_luot_huu_ich} dem san: mot cau GROUP BY
     * tren tap id cua trang la du, va khong sinh ra bat bien hai nguon su that nao de
     * phai canh.
     */
    @Query("SELECT h.maDanhGia, COUNT(h) FROM DanhGiaHuuIch h "
            + "WHERE h.maDanhGia IN :danhSachMa GROUP BY h.maDanhGia")
    List<Object[]> demTheoDanhGia(@Param("danhSachMa") Collection<Long> danhSachMa);

    /** Nhung danh gia trong trang ma chinh nguoi dang xem da binh chon. */
    @Query("SELECT h.maDanhGia FROM DanhGiaHuuIch h "
            + "WHERE h.maNguoiDung = :maNguoiDung AND h.maDanhGia IN :danhSachMa")
    List<Long> timDaBinhChon(@Param("maNguoiDung") int maNguoiDung,
                             @Param("danhSachMa") Collection<Long> danhSachMa);
}
