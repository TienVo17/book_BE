package com.example.book_be.controller.admin;

import com.example.book_be.bo.SachBo;
import com.example.book_be.dao.NguoiDungRepository;
import com.example.book_be.dao.QuyenRepository;
import com.example.book_be.dao.SachRepository;
import com.example.book_be.dao.SuDanhGiaRepository;
import com.example.book_be.entity.Quyen;
import com.example.book_be.entity.Sach;
import com.example.book_be.entity.SuDanhGia;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:3000/")
@RequestMapping("api/admin/danh-gia")
public class BinhLuanController {
    @Autowired
    private SuDanhGiaRepository suDanhGiaRepository;
    @Autowired
    private NguoiDungRepository nguoiDungRepository;

    @Autowired
    private SachRepository sachRepository;

    @GetMapping("findAll")
    public Page<SuDanhGia> findAll(@RequestParam("page") Integer page) {
        Pageable pageable = PageRequest.of(page,10);
        Page<SuDanhGia> suDanhGiaPage = suDanhGiaRepository.findAll((root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();


            return builder.and(predicates.toArray(new Predicate[0]));
        }, pageable);

        return suDanhGiaPage;
    }


    @PostMapping("active/{id}")
    public ResponseEntity<?> active(@PathVariable Long id) {
        SuDanhGia suDanhGia = suDanhGiaRepository.findById(id).orElse(null);
        suDanhGia.setIsActive(1);
        suDanhGiaRepository.save(suDanhGia);
        return null;
    }

    @PostMapping("unactive/{id}")
    public ResponseEntity<?> unactive(@PathVariable Long id) {
        SuDanhGia suDanhGia = suDanhGiaRepository.findById(id).orElse(null);
        suDanhGia.setIsActive(0);
        suDanhGiaRepository.save(suDanhGia);
        return null;
    }


}
