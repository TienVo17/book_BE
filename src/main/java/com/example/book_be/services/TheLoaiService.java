package com.example.book_be.services;

import com.example.book_be.dto.theloai.TheLoaiAdminResponse;
import com.example.book_be.dto.theloai.TheLoaiAdminUpsertRequest;
import com.example.book_be.dto.theloai.TheLoaiResponse;

import java.util.List;

public interface TheLoaiService {
    List<TheLoaiResponse> getDanhSachTheLoaiPublic();

    TheLoaiResponse getTheLoaiPublicBySlug(String slug);

    List<TheLoaiAdminResponse> getDanhSachTheLoaiAdmin();

    TheLoaiAdminResponse taoTheLoai(TheLoaiAdminUpsertRequest request);

    TheLoaiAdminResponse capNhatTheLoai(Integer maTheLoai, TheLoaiAdminUpsertRequest request);

    void xoaTheLoai(Integer maTheLoai);
}
