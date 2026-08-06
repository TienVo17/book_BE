package com.example.book_be.danhgia.repository;

import com.example.book_be.danhgia.domain.SuDanhGia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

/**
 * Khong expose qua Spring Data REST.
 *
 * <p>Truoc day repository nay mang {@code @RepositoryRestResource(path = "su-danh-gia")}.
 * Chan collection/item trong RestConfig van khong du: {@code /sach/{id}/listDanhGia} la
 * association cua <b>Sach</b>, khong phai cua SuDanhGia, nen no khong chiu bat ky bo loc
 * trang thai nao va tra ve ca danh gia da bi admin an cho khach an danh.
 * {@code exported = false} lam Spring Data REST khong sinh ra link nao, ke ca link
 * association tu Sach — do la ly do chon cach nay thay vi chan association tren Sach
 * (se giet luon /sach/{id}/listTheLoai, association duy nhat cua Sach van con mo va
 * dang tra 200; /sach/{id}/listHinhAnh von da dong tu truoc vi HinhAnhRepository la
 * exported = false).
 */
@RepositoryRestResource(exported = false)
public interface SuDanhGiaRepository extends JpaRepository<SuDanhGia, Long>, JpaSpecificationExecutor<SuDanhGia> {

    /**
     * Tinh lai diem trung binh va so luot danh gia cua mot cuon sach tu cac danh gia
     * dang o trang thai hien thi.
     *
     * <p>{@code flushAutomatically = true} la bat buoc chu khong phai tuy chon: JPQL UPDATE
     * khong nhin thay thay doi con nam trong persistence context. {@code updateReview} sua
     * entity roi save (merge, chua sinh SQL) va {@code deleteReview} goi remove (hoan toi
     * flush) — thieu flush thi cau SELECT ben duoi doc du lieu cu va ghi ra so sai vinh vien.
     * {@code clearAutomatically = true} de Sach trong context khong giu lai gia tri cu sau
     * bulk update. Cung khuon voi {@code SachRepository.truKhoNeuDu}.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Sach s SET "
            + "s.trungBinhXepHang = COALESCE((SELECT AVG(d.diemXepHang) FROM SuDanhGia d "
            + "    WHERE d.sach.maSach = :maSach AND d.trangThai = com.example.book_be.danhgia.domain.TrangThaiDanhGia.HIEN_THI), 0), "
            + "s.soLuotDanhGia = (SELECT COUNT(d) FROM SuDanhGia d "
            + "    WHERE d.sach.maSach = :maSach AND d.trangThai = com.example.book_be.danhgia.domain.TrangThaiDanhGia.HIEN_THI) "
            + "WHERE s.maSach = :maSach")
    int capNhatTongHopChoSach(@Param("maSach") int maSach);

    /** Tinh lai toan bo — duong sua chua khi mot du lieu tong hop da lech. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Sach s SET "
            + "s.trungBinhXepHang = COALESCE((SELECT AVG(d.diemXepHang) FROM SuDanhGia d "
            + "    WHERE d.sach.maSach = s.maSach AND d.trangThai = com.example.book_be.danhgia.domain.TrangThaiDanhGia.HIEN_THI), 0), "
            + "s.soLuotDanhGia = (SELECT COUNT(d) FROM SuDanhGia d "
            + "    WHERE d.sach.maSach = s.maSach AND d.trangThai = com.example.book_be.danhgia.domain.TrangThaiDanhGia.HIEN_THI)")
    int capNhatTongHopTatCa();
}
