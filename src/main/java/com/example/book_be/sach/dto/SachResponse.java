package com.example.book_be.sach.dto;

import com.example.book_be.sach.domain.Sach;
import com.example.book_be.sach.domain.SachThongTinChiTiet;
import com.example.book_be.sach.domain.TheLoai;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO tra ve o bien API cho Sach — KHONG expose truc tiep JPA entity.
 * Mirror chinh xac shape JSON hien tai (cung ten field, cung thu tu, tai dung
 * type nested khong nhay cam) de khong doi response shape / khong vo frontend.
 */
@Data
public class SachResponse {
    private int maSach;
    private String tenSach;
    private String tenTacGia;
    private String moTa;
    private String moTaNgan;
    private String moTaChiTiet;
    private double giaNiemYet;
    private double giaBan;
    private int soLuong;
    private double trungBinhXepHang;
    private String ISBN;
    private String slug;
    private Integer isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private SachThongTinChiTiet thongTinChiTiet;
    private List<TheLoai> listTheLoai;

    public static SachResponse from(Sach s) {
        if (s == null) {
            return null;
        }
        SachResponse r = new SachResponse();
        r.maSach = s.getMaSach();
        r.tenSach = s.getTenSach();
        r.tenTacGia = s.getTenTacGia();
        r.moTa = s.getMoTa();
        r.moTaNgan = s.getMoTaNgan();
        r.moTaChiTiet = s.getMoTaChiTiet();
        r.giaNiemYet = s.getGiaNiemYet();
        r.giaBan = s.getGiaBan();
        r.soLuong = s.getSoLuong();
        r.trungBinhXepHang = s.getTrungBinhXepHang();
        r.ISBN = s.getISBN();
        r.slug = s.getSlug();
        r.isActive = s.getIsActive();
        r.createdAt = s.getCreatedAt();
        r.updatedAt = s.getUpdatedAt();
        r.thongTinChiTiet = s.getThongTinChiTiet();
        r.listTheLoai = s.getListTheLoai();
        return r;
    }

    public static List<SachResponse> fromList(List<Sach> list) {
        return list == null ? null : list.stream().map(SachResponse::from).toList();
    }

    public static Page<SachResponse> fromPage(Page<Sach> page) {
        return page == null ? null : page.map(SachResponse::from);
    }
}
