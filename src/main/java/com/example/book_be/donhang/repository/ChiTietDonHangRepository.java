package com.example.book_be.donhang.repository;

import com.example.book_be.donhang.domain.ChiTietDonHang;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

/**
 * Repository for ChiTietDonHang (order line items).
 * Includes queries for top best-selling books aggregation.
 */
/** exported=false: chi tiet don hang la nhay cam, khong co API cong khai/user can den truc tiep. */
@RepositoryRestResource(path = "chi-tiet-don-hang", exported = false)
public interface ChiTietDonHangRepository extends JpaRepository<ChiTietDonHang, Long>, JpaSpecificationExecutor {

    // Returns Object[] rows: [maSach, tenSach, tongBan] ordered by sales volume desc
    // Loai don demo (V12): chung ton tai de danh gia seed co bang chung da mua, khong phai
    // vi ai do that su mua cuon sach do. De lai thi bang ban chay bi xao lai boi du lieu dan dung.
    @Query("SELECT ct.sach.maSach, ct.sach.tenSach, SUM(ct.soLuong) as tongBan FROM ChiTietDonHang ct "
            + "WHERE ct.donHang.laDonDemo = false "
            + "GROUP BY ct.sach.maSach, ct.sach.tenSach ORDER BY tongBan DESC")
    List<Object[]> findTopBanChay(Pageable pageable);
}
