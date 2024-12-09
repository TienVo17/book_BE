package com.example.book_be.services.admin;

import com.example.book_be.bo.PhanQuyenBo;
import com.example.book_be.bo.UserBo;
import com.example.book_be.entity.NguoiDung;
import com.example.book_be.entity.Sach;
import org.springframework.data.domain.Page;

public interface AdminUserService {
    Page<NguoiDung> findAll(UserBo model);

    NguoiDung save(UserBo model);

    NguoiDung update(UserBo model);

    NguoiDung delete(Long id);

    NguoiDung findById(Long id);

    void phanQuyen(PhanQuyenBo phanQuyenBo);
}
