package com.example.book_be.danhgia.dto;

import com.example.book_be.danhgia.domain.SuDanhGia;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.sql.Timestamp;
import java.util.List;

/**
 * DTO tra ve o bien API cho SuDanhGia — KHONG expose truc tiep JPA entity.
 * Mirror chinh xac shape JSON hien tai (cung ten field, cung thu tu) de khong
 * doi response shape / khong vo frontend.
 */
@Data
public class DanhGiaResponse {
    private long maDanhGia;
    private String nhanXet;
    private float diemXepHang;
    private Timestamp timestamp;
    private Integer maNguoiDung;
    private Integer isActive;

    public static DanhGiaResponse from(SuDanhGia d) {
        if (d == null) {
            return null;
        }
        DanhGiaResponse r = new DanhGiaResponse();
        r.maDanhGia = d.getMaDanhGia();
        r.nhanXet = d.getNhanXet();
        r.diemXepHang = d.getDiemXepHang();
        r.timestamp = d.getTimestamp();
        r.maNguoiDung = d.getMaNguoiDung();
        r.isActive = d.getIsActive();
        return r;
    }

    public static List<DanhGiaResponse> fromList(List<SuDanhGia> list) {
        return list.stream().map(DanhGiaResponse::from).toList();
    }

    public static Page<DanhGiaResponse> fromPage(Page<SuDanhGia> page) {
        return page.map(DanhGiaResponse::from);
    }
}
