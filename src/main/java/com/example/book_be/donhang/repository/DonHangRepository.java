package com.example.book_be.donhang.repository;

import com.example.book_be.donhang.domain.DonHang;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Optional;

/**
 * Repository for DonHang (orders).
 * Includes aggregate queries for admin dashboard statistics.
 *
 * <p>Moi truy van thong ke deu loai {@code laDonDemo = true}. Don demo do V12 sinh ra chi
 * ton tai de danh gia seed co bang chung da mua; tinh chung vao dashboard thi so lieu
 * quan tri tro thanh so ao. Loai o mot vai truy van thoi con te hon — dashboard se tu
 * mau thuan voi chinh no (doanh thu khong tinh don do nhung tong so don thi co).
 */
/** exported=false: order data la nhay cam, chi /api/don-hang/** (co kiem tra ownership) duoc dung. */
@RepositoryRestResource(path = "don-hang", exported = false)
public interface DonHangRepository extends JpaRepository<DonHang, Long>, JpaSpecificationExecutor {

    // Doanh thu KHONG tinh don da huy (trangThaiGiaoHang = 3) du da thanh toan.
    @Query("SELECT COALESCE(SUM(d.tongTien), 0) FROM DonHang d WHERE d.trangThaiThanhToan = 1 AND (d.trangThaiGiaoHang IS NULL OR d.trangThaiGiaoHang <> 3) AND d.laDonDemo = false")
    double sumDoanhThu();

    @Query("SELECT COUNT(d) FROM DonHang d WHERE FUNCTION('DATE', d.ngayTao) = CURRENT_DATE AND d.laDonDemo = false")
    long countDonHangHomNay();

    @Query("SELECT COALESCE(SUM(d.tongTien), 0) FROM DonHang d WHERE d.trangThaiThanhToan = 1 AND (d.trangThaiGiaoHang IS NULL OR d.trangThaiGiaoHang <> 3) AND FUNCTION('DATE', d.ngayTao) = CURRENT_DATE AND d.laDonDemo = false")
    double sumDoanhThuHomNay();

    /** Tong so don that. Thay cho {@code count()} cua JpaRepository, von dem ca don demo. */
    @Query("SELECT COUNT(d) FROM DonHang d WHERE d.laDonDemo = false")
    long demDonThat();

    @Query("SELECT COUNT(d) FROM DonHang d WHERE d.trangThaiGiaoHang = :trangThai AND d.laDonDemo = false")
    long demTheoTrangThaiGiaoHang(@Param("trangThai") Integer trangThai);

    long countByTrangThaiGiaoHang(Integer trangThaiGiaoHang);

    /**
     * Don DA_GIAO (2) moi nhat cua nguoi dung co chua cuon sach nay.
     *
     * <p>Mot truy van duy nhat cho ca trang san pham; index
     * {@code idx_don_hang_nguoi_trang_thai} phuc vu dung menh de nay. Lay don MOI NHAT vi
     * mua lai cung mot cuon sach nhieu lan la chuyen binh thuong, va bang chung nen tro
     * toi lan mua gan nhat.
     */
    @Query("SELECT MAX(d.maDonHang) FROM DonHang d JOIN d.danhSachChiTietDonHang ct "
            + "WHERE d.nguoiDung.maNguoiDung = :maNguoiDung AND ct.sach.maSach = :maSach "
            + "AND d.trangThaiGiaoHang = 2")
    Integer timDonDaGiaoChoSach(@Param("maNguoiDung") int maNguoiDung, @Param("maSach") int maSach);

    /** Nguoi dung co don nao chua sach nay khong, o bat ky trang thai giao hang nao. */
    @Query("SELECT COUNT(d) FROM DonHang d JOIN d.danhSachChiTietDonHang ct "
            + "WHERE d.nguoiDung.maNguoiDung = :maNguoiDung AND ct.sach.maSach = :maSach")
    long demDonChuaSach(@Param("maNguoiDung") int maNguoiDung, @Param("maSach") int maSach);

    @Query("SELECT DISTINCT d FROM DonHang d "
            + "LEFT JOIN FETCH d.nguoiDung "
            + "LEFT JOIN FETCH d.hinhThucThanhToan "
            + "LEFT JOIN FETCH d.hinhThucGiaoHang "
            + "LEFT JOIN FETCH d.danhSachChiTietDonHang ct "
            + "LEFT JOIN FETCH ct.sach "
            + "WHERE d.maDonHang = :id")
    Optional<DonHang> findDetailById(@Param("id") Long id);

    /** Tra cuu don da tao boi cung nguoi dung + cung Idempotency-Key (phuc vu replay/claim khi checkout). */
    Optional<DonHang> findByNguoiDung_MaNguoiDungAndCheckoutIdempotencyKey(int maNguoiDung, String checkoutIdempotencyKey);
}
