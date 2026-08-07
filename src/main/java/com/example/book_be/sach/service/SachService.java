package com.example.book_be.sach.service;

import com.example.book_be.sach.dto.SachAdminUpsertBo;
import com.example.book_be.sach.dto.SachBo;
import com.example.book_be.sach.dto.SachGoiYResponse;
import com.example.book_be.sach.dto.SachTonKhoResponse;
import com.example.book_be.sach.domain.Sach;
import org.springframework.data.domain.Page;

import java.util.List;

public interface SachService {
    /** Gia tri hop le cho tham so {@code sort} cua GET /api/sach — controller validate truoc khi goi service. */
    String SORT_MOI_NHAT = "moi-nhat";
    String SORT_GIA_TANG = "gia-tang";
    String SORT_GIA_GIAM = "gia-giam";
    String SORT_TEN_AZ = "ten-az";
    String SORT_DANH_GIA = "danh-gia";

    Page<Sach> findAll(SachBo bo);
    Sach save(Sach sach);
    Sach save(SachAdminUpsertBo bo);
    Sach update(Sach bo) throws Exception;
    Sach update(SachAdminUpsertBo bo) throws Exception;
    SachTonKhoResponse dieuChinhTonKho(Long maSach, Integer soLuongThayDoi);
    Sach delete(Long id);
    Sach findById(Long id);
    Sach active(Long id);
    Sach unactive(Long id);
    List<Sach> findBanChay(int limit);
    List<Sach> findMoiNhat(int limit);
    List<Sach> findLienQuan(int maSach, int limit);
    Sach findBySlug(String slug);
    List<SachGoiYResponse> findGoiY(String tuKhoa, int limit);
}
