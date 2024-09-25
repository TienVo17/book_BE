package com.example.book_be.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.List;
@Data
@Entity
@Table(name = "quyen")
public class Quyen {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_quyen")
    private int maQuyen;
    @Column(name = "ten_quyen")
    private String tenQuyen;
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH, CascadeType.REFRESH})
    @JoinTable(name = "nguoidung_quyen", joinColumns = @JoinColumn(name = "ma_quyen"), inverseJoinColumns = @JoinColumn(name = "ma_nguoi_dung"))
    List<NguoiDung> danhSachNguoiDung;
}
