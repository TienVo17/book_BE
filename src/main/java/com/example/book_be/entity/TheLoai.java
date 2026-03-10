package com.example.book_be.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.util.List;

@Entity
@Data
@Table(name = "the_loai")
public class TheLoai {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="ma_the_loai")
    private int maTheLoai;
    @Column(name= "ten_the_loai",length = 256)
    private String tenTheLoai;
    @Column(name = "slug", nullable = false, unique = true, length = 255)
    private String slug;
    @JsonIgnore
    @ManyToMany(fetch = FetchType.LAZY, cascade = {
            CascadeType.PERSIST, CascadeType.MERGE,
            CascadeType.DETACH, CascadeType.REFRESH})
    @JoinTable(name = "sach_theloai", joinColumns = @JoinColumn(name = "ma_the_loai"), inverseJoinColumns = @JoinColumn(name = "ma_sach"))
    private List<Sach> listSach;
}
