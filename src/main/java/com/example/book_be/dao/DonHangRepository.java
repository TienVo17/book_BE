package com.example.book_be.dao;

import com.example.book_be.entity.DonHang;
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

    @Query("SELECT COALESCE(SUM(d.tongTien), 0) FROM DonHang d WHERE d.trangThaiThanhToan = 1")
    double sumDoanhThu();

    @Query("SELECT COUNT(d) FROM DonHang d WHERE FUNCTION('DATE', d.ngayTao) = CURRENT_DATE")
    long countDonHangHomNay();

    @Query("SELECT COALESCE(SUM(d.tongTien), 0) FROM DonHang d WHERE d.trangThaiThanhToan = 1 AND FUNCTION('DATE', d.ngayTao) = CURRENT_DATE")
    double sumDoanhThuHomNay();

    long countByTrangThaiGiaoHang(Integer trangThaiGiaoHang);
}
