package com.example.book_be.controller;

import com.example.book_be.bo.DanhGiaBo;
import com.example.book_be.dao.NguoiDungRepository;
import com.example.book_be.dao.SuDanhGiaRepository;
import com.example.book_be.entity.NguoiDung;
import com.example.book_be.entity.SuDanhGia;
import com.example.book_be.services.review.DanhGiaService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("api/danh-gia")
public class DanhGiaController {
    @Autowired
    private DanhGiaService danhGiaService;

    @Autowired
    private NguoiDungRepository nguoiDungRepository;

    @Autowired
    private SuDanhGiaRepository suDanhGiaRepository;

    @GetMapping("findAll")
    public List<SuDanhGia> findAll(@RequestParam("maSach") Integer maSach) {
        List<SuDanhGia> suDanhGiaPage = suDanhGiaRepository.findAll((root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("sach").get("maSach"),maSach));
            predicates.add(builder.equal(root.get("isActive"),1));
            return builder.and(predicates.toArray(new Predicate[0]));
        });
        return suDanhGiaPage;
    }

    @PostMapping("/them-danh-gia-v1")
    public SuDanhGia addReview(@RequestBody DanhGiaBo danhGia) {
        NguoiDung nguoiDung = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication.getName() != null) {
            nguoiDung = nguoiDungRepository.findByTenDangNhap(authentication.getName());
        }
        NguoiDung finalNguoiDung = nguoiDung;
        SuDanhGia suDanhGia = danhGiaService.addReview(
                danhGia.getNhanXet(),
                danhGia.getDiemXepHang(),
                (long) finalNguoiDung.getMaNguoiDung(),
                (long) danhGia.getMaSach()
        );
        suDanhGia.setSach(null);
        suDanhGia.setNguoiDung(null);
        return suDanhGia;
    }

    @PostMapping("/sua-danh-gia/{maDanhGia}")
    public SuDanhGia updateReview(@PathVariable Long maDanhGia, @RequestBody SuDanhGia danhGia) {
        return danhGiaService.updateReview(
                maDanhGia, danhGia
        );
    }

    @PostMapping("/xoa-danh-gia/{maDanhGia}")
    public SuDanhGia deleteReview(@PathVariable Long maDanhGia) {
        return danhGiaService.deleteReview(
                maDanhGia
        );
    }

}
