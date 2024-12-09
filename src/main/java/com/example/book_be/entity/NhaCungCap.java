package com.example.book_be.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.List;

@Entity
@Data
@Table(name = "nha_cung_cap")
public class NhaCungCap {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_nha_cung_cap")
    private int maNhaCungCap;

    @Column(name = "ten_nha_cung_cap", length = 256)
    private String tenNhaCungCap;

    @OneToMany(mappedBy = "nhaCungCap", fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH, CascadeType.REFRESH})
    private List<Sach> listSach;
}
