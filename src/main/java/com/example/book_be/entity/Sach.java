package com.example.book_be.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import lombok.Data;

import java.time.LocalDateTime;
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

    @Column(name = "mo_ta_ngan", columnDefinition = "text")
    private String moTaNgan;

    @Column(name = "mo_ta_chi_tiet", columnDefinition = "LONGTEXT")
    private String moTaChiTiet;

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

    @Column(name = "slug", unique = true, length = 512)
    private String slug;

    @Column(name = "is_active", columnDefinition = "integer default 1")
    private Integer isActive = 1;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToOne(mappedBy = "sach", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private SachThongTinChiTiet thongTinChiTiet;

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH, CascadeType.REFRESH})
    @JoinTable(name = "sach_theloai", joinColumns = @JoinColumn(name = "ma_sach"), inverseJoinColumns = @JoinColumn(name = "ma_the_loai"))
    List<TheLoai> listTheLoai;

    @JsonIgnore
    @OneToMany(mappedBy = "sach", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH, CascadeType.REFRESH, CascadeType.REMOVE})
    List<HinhAnh> listHinhAnh;

    @JsonIgnore
    @OneToMany(mappedBy = "sach", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH, CascadeType.REFRESH})
    List<SuDanhGia> listDanhGia;

    @JsonIgnore
    @OneToMany(mappedBy = "sach", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH, CascadeType.REFRESH})
    List<ChiTietDonHang> listChiTietDonHang;

    @JsonIgnore
    @OneToMany(mappedBy = "sach", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    List<SachYeuThich> listSachYeuThich;

    @ManyToOne(cascade = {
            CascadeType.DETACH, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.PERSIST
    })
    @JsonIgnore
    @JoinColumn(name = "ma_nha_cung_cap", nullable = true)
    private NhaCungCap nhaCungCap;

    @JsonIgnore
    @OneToMany(mappedBy = "sach", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    List<GioHang> gioHangList;

    @JsonIgnore
    @Transient
    private List<String> listImageStr;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        syncDescriptionFields();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
        syncDescriptionFields();
    }

    private void syncDescriptionFields() {
        if ((moTaChiTiet == null || moTaChiTiet.isBlank()) && moTa != null && !moTa.isBlank()) {
            moTaChiTiet = moTa;
        }
        if ((moTa == null || moTa.isBlank()) && moTaChiTiet != null && !moTaChiTiet.isBlank()) {
            moTa = moTaChiTiet;
        }
    }
}
