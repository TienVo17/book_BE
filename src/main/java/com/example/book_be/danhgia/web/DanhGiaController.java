package com.example.book_be.danhgia.web;

import com.example.book_be.danhgia.dto.DanhGiaBo;
import com.example.book_be.danhgia.dto.DanhGiaResponse;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.danhgia.repository.SuDanhGiaRepository;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.danhgia.service.DanhGiaService;
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
    public List<DanhGiaResponse> findAll(@RequestParam("maSach") Integer maSach) {
        List<SuDanhGia> suDanhGiaPage = suDanhGiaRepository.findAll((root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("sach").get("maSach"),maSach));
            predicates.add(builder.equal(root.get("isActive"),1));
            return builder.and(predicates.toArray(new Predicate[0]));
        });
        return DanhGiaResponse.fromList(suDanhGiaPage);
    }

    @PostMapping("/them-danh-gia-v1")
    public DanhGiaResponse addReview(@RequestBody DanhGiaBo danhGia) {
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
        return DanhGiaResponse.from(suDanhGia);
    }

    @PostMapping("/sua-danh-gia/{maDanhGia}")
    public DanhGiaResponse updateReview(@PathVariable Long maDanhGia, @RequestBody SuDanhGia danhGia) {
        return DanhGiaResponse.from(danhGiaService.updateReview(
                maDanhGia, danhGia
        ));
    }

    @PostMapping("/xoa-danh-gia/{maDanhGia}")
    public DanhGiaResponse deleteReview(@PathVariable Long maDanhGia) {
        return DanhGiaResponse.from(danhGiaService.deleteReview(
                maDanhGia
        ));
    }

}
