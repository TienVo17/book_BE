package com.example.book_be.danhgia.web;

import com.example.book_be.danhgia.dto.CoTheDanhGiaResponse;
import com.example.book_be.danhgia.dto.DanhGiaBo;
import com.example.book_be.danhgia.dto.DanhGiaResponse;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.danhgia.repository.SuDanhGiaRepository;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.danhgia.domain.TrangThaiDanhGia;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
            predicates.add(builder.equal(root.get("sach").get("maSach"), maSach));
            predicates.add(builder.equal(root.get("trangThai"), TrangThaiDanhGia.HIEN_THI));
            return builder.and(predicates.toArray(new Predicate[0]));
        });
        return DanhGiaResponse.fromList(suDanhGiaPage);
    }

    /**
     * Cho giao dien biet truoc co nen hien form khong, va neu khong thi vi sao.
     *
     * <p>Day KHONG phai cho cuong che — {@code addReview} tu kiem tra lai. Endpoint chi
     * ton tai de nguoi dung khong go xong ca bai roi moi bi tu choi.
     */
    @GetMapping("/co-the-danh-gia")
    public CoTheDanhGiaResponse coTheDanhGia(@RequestParam("maSach") Integer maSach) {
        if (maSach == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu mã sách.");
        }
        NguoiDung nguoiDung = nguoiDungHienTai();
        return danhGiaService.kiemTraCoTheDanhGia(nguoiDung.getMaNguoiDung(), maSach);
    }

    @PostMapping("/them-danh-gia-v1")
    public DanhGiaResponse addReview(@RequestBody DanhGiaBo danhGia) {
        if (danhGia == null || danhGia.getMaSach() == null || danhGia.getDiemXepHang() == null
                || danhGia.getDiemXepHang() < 1 || danhGia.getDiemXepHang() > 5
                || danhGia.getNhanXet() == null || danhGia.getNhanXet().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thông tin đánh giá không hợp lệ.");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập.");
        }
        NguoiDung nguoiDung = nguoiDungRepository.findByTenDangNhap(authentication.getName());
        if (nguoiDung == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập.");
        }
        SuDanhGia suDanhGia = danhGiaService.addReview(
                danhGia.getNhanXet(),
                danhGia.getDiemXepHang(),
                (long) nguoiDung.getMaNguoiDung(),
                (long) danhGia.getMaSach()
        );
        return DanhGiaResponse.from(suDanhGia);
    }

    @PostMapping("/sua-danh-gia/{maDanhGia}")
    public DanhGiaResponse updateReview(@PathVariable Long maDanhGia, @RequestBody SuDanhGia danhGia) {
        NguoiDung nguoiDung = nguoiDungHienTai();
        return DanhGiaResponse.from(danhGiaService.updateReview(
                maDanhGia, danhGia, (long) nguoiDung.getMaNguoiDung()
        ));
    }

    @PostMapping("/xoa-danh-gia/{maDanhGia}")
    public DanhGiaResponse deleteReview(@PathVariable Long maDanhGia) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        NguoiDung nguoiDung = nguoiDungHienTai();
        boolean laQuanTri = authentication.getAuthorities().stream()
                .anyMatch(quyen -> "ADMIN".equals(quyen.getAuthority()));
        return DanhGiaResponse.from(danhGiaService.deleteReview(
                maDanhGia, (long) nguoiDung.getMaNguoiDung(), laQuanTri
        ));
    }

    /** Principal da duoc SecurityConfiguration xac thuc; van kiem tra lai de fail-closed. */
    private NguoiDung nguoiDungHienTai() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập.");
        }
        NguoiDung nguoiDung = nguoiDungRepository.findByTenDangNhap(authentication.getName());
        if (nguoiDung == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập.");
        }
        return nguoiDung;
    }

}
