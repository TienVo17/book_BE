package com.example.book_be.sach.service;

import com.example.book_be.sach.dto.TheLoaiAdminResponse;
import com.example.book_be.sach.dto.TheLoaiAdminUpsertRequest;
import com.example.book_be.sach.dto.TheLoaiResponse;

import java.util.List;

public interface TheLoaiService {
    List<TheLoaiResponse> getDanhSachTheLoaiPublic();

    TheLoaiResponse getTheLoaiPublicBySlug(String slug);

    List<TheLoaiAdminResponse> getDanhSachTheLoaiAdmin();

    TheLoaiAdminResponse taoTheLoai(TheLoaiAdminUpsertRequest request);

    TheLoaiAdminResponse capNhatTheLoai(Integer maTheLoai, TheLoaiAdminUpsertRequest request);

    void xoaTheLoai(Integer maTheLoai);
}
