package com.example.book_be.nguoidung.service;

import com.example.book_be.nguoidung.dto.PhanQuyenBo;
import com.example.book_be.nguoidung.dto.UserBo;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.sach.domain.Sach;
import org.springframework.data.domain.Page;

public interface AdminUserService {
    Page<NguoiDung> findAll(UserBo model);

    NguoiDung save(UserBo model);

    NguoiDung update(UserBo model);

    NguoiDung delete(Long id);

    NguoiDung findById(Long id);

    void phanQuyen(PhanQuyenBo phanQuyenBo);
}
