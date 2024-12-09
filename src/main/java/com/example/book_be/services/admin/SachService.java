package com.example.book_be.services.admin;

import com.example.book_be.bo.SachBo;
import com.example.book_be.entity.Sach;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;


public interface SachService {
    Page<Sach> findAll(SachBo bo);

    Sach save(Sach sach);

    Sach update(Sach bo) throws Exception;

    Sach delete(Long id);

    Sach findById(Long id);

    Sach active(Long id);

    Sach unactive(Long id);
}
