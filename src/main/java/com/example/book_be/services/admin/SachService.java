package com.example.book_be.services.admin;

import com.example.book_be.bo.SachAdminUpsertBo;
import com.example.book_be.bo.SachBo;
import com.example.book_be.entity.Sach;
import org.springframework.data.domain.Page;

import java.util.List;

public interface SachService {
    Page<Sach> findAll(SachBo bo);
    Page<Sach> findBookByName(String tenSach, int page, int size);
    Sach save(Sach sach);
    Sach save(SachAdminUpsertBo bo);
    Sach update(Sach bo) throws Exception;
    Sach update(SachAdminUpsertBo bo) throws Exception;
    Sach delete(Long id);
    Sach findById(Long id);
    Sach active(Long id);
    Sach unactive(Long id);
    List<Sach> findBanChay(int limit);
    List<Sach> findMoiNhat(int limit);
    List<Sach> findLienQuan(int maSach, int limit);
    Sach findBySlug(String slug);
}
