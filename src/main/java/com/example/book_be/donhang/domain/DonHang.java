package com.example.book_be.donhang.domain;
import com.example.book_be.thanhtoan.domain.HinhThucThanhToan;
import com.example.book_be.nguoidung.domain.NguoiDung;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Locale;

@Data
@Entity
@Table(name = "don_hang")
public class DonHang {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_don_hang")
    private int maDonHang;
    @Column(name = "ngay_tao")
    private Date ngayTao;
    @Column(name = "dia_chi_mua_hang", length = 512)
    private String diaChiMuaHang;
    @Column(name = "dia_chi_nhan_hang", length = 512)
    private String diaChiNhanHang;
    @Column(name = "tong_tien_san_pham")
    private double tongTienSanPham;
    @Column(name = "chi_phi_giao_hang")
    private double chiPhiGiaoHang;
    @Column(name = "chi_phi_thanh_toan")
    private double chiPhiThanhToan;

    private double tongTien;

    @Column(name = "ho_ten")
    private String hoTen;
    @Column(name = "so_dien_thoai")
    private String soDienThoai;



    @JsonIgnore
    @OneToMany(mappedBy = "donHang", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH, CascadeType.REFRESH, CascadeType.REMOVE})
    private List<ChiTietDonHang> danhSachChiTietDonHang;
    @ManyToOne(cascade = {
            CascadeType.DETACH, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.PERSIST
    })
    @JsonIgnore
    @JoinColumn(name = "ma_nguoi_dung", nullable = false)
    private NguoiDung nguoiDung;
    @ManyToOne(cascade = {
            CascadeType.DETACH, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.PERSIST
    })
    @JsonIgnore
    @JoinColumn(name = "ma_hinh_thuc_thanh_toan")
    private HinhThucThanhToan hinhThucThanhToan;
    @ManyToOne(cascade = {
            CascadeType.DETACH, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.PERSIST
    })
    @JsonIgnore
    @JoinColumn(name = "ma_hinh_thuc_giao_hang")
    private HinhThucGiaoHang hinhThucGiaoHang;

    @Column(name = "trang_thai_thanh_toan")
    private Integer trangThaiThanhToan;

    @Column(name = "trang_thai_giao_hang")
    private Integer trangThaiGiaoHang;

    /** Coupon da dung cho don (neu co) - phuc vu hoan luot coupon khi huy. Khong expose ra JSON. */
    @JsonIgnore
    @Column(name = "ma_coupon")
    private Integer maCoupon;

    /** Optimistic lock chong double-cancel / race voi VNPay callback. Khong expose ra JSON. */
    @Version
    @JsonIgnore
    @Column(name = "version")
    private Long version;

    /** Idempotency-Key header cua request checkout tao don nay (null = legacy/khong dung key). */
    @JsonIgnore
    @Column(name = "checkout_idempotency_key", length = 100)
    private String checkoutIdempotencyKey;

    /** Hash on dinh cua request checkout da normalize (sach+so luong sap xep, dia chi, thanh toan, coupon). */
    @JsonIgnore
    @Column(name = "checkout_request_fingerprint", length = 128)
    private String checkoutRequestFingerprint;

    /** Snapshot bat bien cua coupon code da tra trong CheckoutOrderResponse, khong lookup lai khi replay. */
    @JsonIgnore
    @Column(name = "checkout_response_coupon_code", length = 255)
    private String checkoutResponseCouponCode;

    /** Snapshot bat bien cua payment method da tra trong CheckoutOrderResponse. */
    @JsonIgnore
    @Column(name = "checkout_response_payment_method", length = 20)
    private String checkoutResponsePaymentMethod;

    /** Snapshot trang thai thanh toan tai luc tao response; state cua don co the doi sau VNPay callback. */
    @JsonIgnore
    @Column(name = "checkout_response_payment_status")
    private Integer checkoutResponsePaymentStatus;

    @Transient
    public String getPhuongThucThanhToan() {
        if (hinhThucThanhToan == null || hinhThucThanhToan.getMaCode() == null) {
            return null;
        }
        return hinhThucThanhToan.getMaCode().toUpperCase(Locale.ROOT);
    }

    @Transient
    public String getTenPhuongThucThanhToan() {
        if (hinhThucThanhToan == null) {
            return null;
        }
        return hinhThucThanhToan.getTenHinhThucGiaoHang();
    }
}
