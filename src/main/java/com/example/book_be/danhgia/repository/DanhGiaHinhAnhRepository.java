package com.example.book_be.danhgia.repository;

import com.example.book_be.danhgia.domain.DanhGiaHinhAnh;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Khong expose qua Spring Data REST. */
@RepositoryRestResource(exported = false)
public interface DanhGiaHinhAnhRepository extends JpaRepository<DanhGiaHinhAnh, Long> {

    List<DanhGiaHinhAnh> findByMaDanhGiaOrderByThuTuAsc(long maDanhGia);

    long countByMaDanhGia(long maDanhGia);

    Optional<DanhGiaHinhAnh> findByMaDanhGiaAndIdempotencyKey(
            long maDanhGia, String idempotencyKey);

    /** Nap anh cho CA trang trong mot truy van, khong phai mot truy van moi dong. */
    List<DanhGiaHinhAnh> findByMaDanhGiaInOrderByThuTuAsc(Collection<Long> danhSachMa);

    /**
     * Bo dem han ngach TRON DOI, chi cong len va khong bi buoc xoa lam giam.
     *
     * <p>Gioi han "5 anh moi danh gia" khong phai han ngach: tai 5, xoa, tai 5, lap mai.
     * Cot {@code nguoi_dung.so_anh_danh_gia_da_dung} (V13) moi chan duoc vong do.
     * {@code UPDATE} co dieu kien thay vi doc-roi-ghi, cung khuon voi
     * {@code SachRepository.truKhoNeuDu}: 0 dong tra ve nghia la da het han ngach.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE NguoiDung u SET u.soAnhDanhGiaDaDung = u.soAnhDanhGiaDaDung + 1 "
            + "WHERE u.maNguoiDung = :maNguoiDung AND u.soAnhDanhGiaDaDung < :hanNgach")
    int tangHanNgachNeuCon(@Param("maNguoiDung") int maNguoiDung, @Param("hanNgach") int hanNgach);
}
