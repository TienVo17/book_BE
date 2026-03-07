package com.example.book_be.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;

@Data
@Entity
@Table(name = "sach_thong_tin_chi_tiet")
public class SachThongTinChiTiet {
    @Id
    @Column(name = "ma_sach")
    private Integer maSach;

    @JsonIgnore
    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_sach")
    private Sach sach;

    @Column(name = "cong_ty_phat_hanh", length = 255)
    private String congTyPhatHanh;

    @Column(name = "nha_xuat_ban", length = 255)
    private String nhaXuatBan;

    @Column(name = "ngay_xuat_ban")
    private LocalDate ngayXuatBan;

    @Column(name = "so_trang")
    private Integer soTrang;

    @Column(name = "loai_bia", length = 100)
    private String loaiBia;

    @Column(name = "ngon_ngu", length = 100)
    private String ngonNgu;

    @Column(name = "kich_thuoc", length = 100)
    private String kichThuoc;

    @Column(name = "trong_luong_gram")
    private Integer trongLuongGram;

    @Column(name = "phien_ban", length = 100)
    private String phienBan;
}
