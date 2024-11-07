package com.example.book_be.entity;


import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Entity
@Data
@Table(name = "sach")
public class Sach {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_sach")
    private int maSach;
    @Column(name = "ten_sach", length = 256)
    private String tenSach;
    @Column(name = "ten_tac_gia", length = 512)
    private String tenTacGia;
    @Column(name = "mo_ta", columnDefinition = "text")
    private String moTa;
    @Column(name = "gia_niem_yet")
    private double giaNiemYet;
    @Column(name = "gia_ban")
    private double giaBan;
    @Column(name = "so_luong")
    private int soLuong;
    @Column(name = "trung_binh_xep_hang")
    private double trungBinhXepHang;
    @Column(name = "isbn", length = 256)
    private String ISBN;
    //    @Column(name = "ma_sach")
//    private int soldQuantity;
//    @Column(name = "ma_sach")// Đã bán bao nhiêu
//    private int discountPercent;// Giảm giá bao nhiêu %
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH, CascadeType.REFRESH})
    @JoinTable(name = "sach_theloai", joinColumns = @JoinColumn(name = "ma_sach"), inverseJoinColumns = @JoinColumn(name = "ma_the_loai"))
    List<TheLoai> listTheLoai;
    @OneToMany(mappedBy = "sach", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH, CascadeType.REFRESH, CascadeType.REMOVE})
    List<HinhAnh> listHinhAnh;
    @OneToMany(mappedBy = "sach", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH, CascadeType.REFRESH})
    List<SuDanhGia> listDanhGia;
    @OneToMany(mappedBy = "sach", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH, CascadeType.REFRESH})
    List<ChiTietDonHang> listChiTietDonHang;
    @OneToMany(mappedBy = "sach", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    List<SachYeuThich> listSachYeuThich;

    @ManyToOne(cascade = {
            CascadeType.DETACH, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.PERSIST
    })
    @JoinColumn(name = "ma_nha_cung_cap", nullable = true)
    private NhaCungCap nhaCungCap;
    @OneToMany(mappedBy = "sach", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    List<GioHang> gioHangList;
}
