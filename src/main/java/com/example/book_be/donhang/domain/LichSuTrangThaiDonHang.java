package com.example.book_be.donhang.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Date;

/**
 * Lich su chuyen trang thai don hang. Moi chuyen hop le (giao hang hoac thanh toan) ghi 1 dong.
 * Bang audit noi bo - khong expose ra REST.
 */
@Data
@Entity
@Table(name = "lich_su_trang_thai_don_hang")
public class LichSuTrangThaiDonHang {

    /** Ten truong trang thai duoc chuyen. */
    public static final String TRUONG_GIAO_HANG = "GIAO_HANG";
    public static final String TRUONG_THANH_TOAN = "THANH_TOAN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_lich_su")
    private Long maLichSu;

    @Column(name = "ma_don_hang", nullable = false)
    private int maDonHang;

    @Column(name = "truong", nullable = false, length = 20)
    private String truong;

    @Column(name = "tu_gia_tri")
    private Integer tuGiaTri;

    @Column(name = "den_gia_tri", nullable = false)
    private int denGiaTri;

    @Column(name = "nguoi_thuc_hien", length = 255)
    private String nguoiThucHien;

    @Column(name = "thoi_diem", nullable = false)
    private Date thoiDiem;
}
