package com.example.book_be.danhgia.domain;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.sach.domain.Sach;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

import java.sql.Timestamp;

@Data
@Entity
@Table(name = "danhgia")
public class SuDanhGia  {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_danh_gia")
    private long maDanhGia;
    @Column(name = "nhan_xet",columnDefinition = "text")
    private String nhanXet;
    @Column(name = "diem_xep_hang")
    private float diemXepHang;
    @Column(name = "timestamp")
    private Timestamp timestamp; // thời gian bình luận


    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH})
    @JoinColumn(name="ma_nguoi_dung",nullable = false)
    private NguoiDung nguoiDung;

    @Column(name = "ma_nguoi_dung", insertable = false, updatable = false)
    private Integer maNguoiDung;

    @ManyToOne(cascade = {
            CascadeType.DETACH,CascadeType.MERGE,CascadeType.REFRESH,CascadeType.PERSIST
    })

    @JsonIgnore
    @JoinColumn(name="ma_sach",nullable = false)
    private Sach sach;

    /** Trang thai kiem duyet duy nhat cua danh gia. */
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "trang_thai", nullable = false)
    private TrangThaiDanhGia trangThai = TrangThaiDanhGia.HIEN_THI;

    /**
     * Da tung bi admin an it nhat mot lan. Giu lai ke ca khi chu so huu sua noi dung,
     * de vong lap "bi an -> tu xoa -> dang lai" khong lam sach vet kiem duyet.
     */
    @Column(name = "tung_bi_an", nullable = false)
    private boolean tungBiAn = false;

    /** Don hang chung minh da nhan hang. Con NULL cho toi khi phase 2 backfill. */
    @Column(name = "ma_don_hang")
    private Integer maDonHang;

    /**
     * Phan hoi cong khai cua shop. Luu thang tren dong danh gia chu khong phai mot bang
     * rieng: cau truc nay lam "moi danh gia toi da mot phan hoi" thanh dieu khong the vi
     * pham, thay vi mot quy tac phai canh.
     */
    @Column(name = "phan_hoi_shop", columnDefinition = "text")
    private String phanHoiShop;

    @Column(name = "phan_hoi_shop_tai")
    private Timestamp phanHoiShopTai;

    @Column(name = "phan_hoi_shop_boi")
    private Integer phanHoiShopBoi;

    /** Duong ghi duy nhat cho trang thai; Lombok khong sinh setter truc tiep. */
    public void datTrangThai(TrangThaiDanhGia trangThaiMoi) {
        this.trangThai = trangThaiMoi;
        if (trangThaiMoi == TrangThaiDanhGia.DA_AN) {
            this.tungBiAn = true;
        }
    }
}
