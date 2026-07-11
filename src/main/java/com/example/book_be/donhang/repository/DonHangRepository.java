package com.example.book_be.donhang.repository;

import com.example.book_be.donhang.domain.DonHang;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

/**
 * Repository for DonHang (orders).
 * Includes aggregate queries for admin dashboard statistics.
 */
@RepositoryRestResource(path = "don-hang")
public interface DonHangRepository extends JpaRepository<DonHang, Long>, JpaSpecificationExecutor {

    // Doanh thu KHONG tinh don da huy (trangThaiGiaoHang = 3) du da thanh toan.
    @Query("SELECT COALESCE(SUM(d.tongTien), 0) FROM DonHang d WHERE d.trangThaiThanhToan = 1 AND (d.trangThaiGiaoHang IS NULL OR d.trangThaiGiaoHang <> 3)")
    double sumDoanhThu();

    @Query("SELECT COUNT(d) FROM DonHang d WHERE FUNCTION('DATE', d.ngayTao) = CURRENT_DATE")
    long countDonHangHomNay();

    @Query("SELECT COALESCE(SUM(d.tongTien), 0) FROM DonHang d WHERE d.trangThaiThanhToan = 1 AND (d.trangThaiGiaoHang IS NULL OR d.trangThaiGiaoHang <> 3) AND FUNCTION('DATE', d.ngayTao) = CURRENT_DATE")
    double sumDoanhThuHomNay();

    long countByTrangThaiGiaoHang(Integer trangThaiGiaoHang);
}
