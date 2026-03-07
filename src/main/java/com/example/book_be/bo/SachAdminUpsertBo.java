package com.example.book_be.bo;

import com.example.book_be.entity.NhaCungCap;
import com.example.book_be.entity.TheLoai;
import lombok.Data;

import java.util.List;

@Data
public class SachAdminUpsertBo {
    private Integer maSach;
    private String tenSach;
    private String tenTacGia;
    private String isbn;
    private String slug;
    private String moTaNgan;
    private String moTaChiTiet;
    private Double giaNiemYet;
    private Double giaBan;
    private Integer soLuongTon;
    private Integer isActive;
    private NhaCungCap nhaCungCap;
    private List<TheLoai> listTheLoai;
    private List<String> listImageStr;
    private SachThongTinChiTietBo chiTiet;
}
