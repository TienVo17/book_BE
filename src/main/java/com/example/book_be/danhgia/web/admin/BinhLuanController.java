package com.example.book_be.danhgia.web.admin;

import com.example.book_be.sach.dto.SachBo;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.repository.QuyenRepository;
import com.example.book_be.sach.repository.SachRepository;
import com.example.book_be.danhgia.dto.DanhGiaQuanTriResponse;
import com.example.book_be.danhgia.dto.PhanHoiShopBo;
import com.example.book_be.nguoidung.domain.NguoiDung;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import com.example.book_be.danhgia.repository.SuDanhGiaRepository;
import com.example.book_be.danhgia.service.DanhGiaService;
import com.example.book_be.nguoidung.domain.Quyen;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.danhgia.domain.TrangThaiDanhGia;
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
@RequestMapping("api/admin/danh-gia")
public class BinhLuanController {
    @Autowired
    private SuDanhGiaRepository suDanhGiaRepository;
    @Autowired
    private NguoiDungRepository nguoiDungRepository;

    @Autowired
    private SachRepository sachRepository;

    @Autowired
    private DanhGiaService danhGiaService;

    @GetMapping("findAll")
    public Page<DanhGiaQuanTriResponse> findAll(@RequestParam("page") Integer page) {
        Pageable pageable = PageRequest.of(page == null || page < 0 ? 0 : page, 10);
        return DanhGiaQuanTriResponse.fromPage(suDanhGiaRepository.timTrangChoQuanTri(pageable));
    }


    /**
     * An/hien danh gia di qua service chu khong ghi thang repository.
     *
     * <p>Day la hai thao tac duy nhat lam doi kha kien cua danh gia, tuc la dung thu ma
     * du lieu tong hop cua sach duoc dinh nghia tren do. Ghi thang repository nhu truoc
     * dong nghia diem trung binh khong bao gio duoc tinh lai khi admin kiem duyet.
     *
     * <p>Truoc day ham nay dung {@code orElse(null)} roi goi setter ngay — id khong ton tai
     * tao NullPointerException va tra ve HTTP 500; va {@code return null} khien client nhan
     * 200 rong, khong phan biet duoc thanh cong hay that bai.
     */
    @PostMapping("active/{id}")
    public ResponseEntity<DanhGiaQuanTriResponse> active(@PathVariable Long id) {
        return ResponseEntity.ok(DanhGiaQuanTriResponse.from(
                danhGiaService.doiTrangThai(id, TrangThaiDanhGia.HIEN_THI)));
    }

    @PostMapping("unactive/{id}")
    public ResponseEntity<DanhGiaQuanTriResponse> unactive(@PathVariable Long id) {
        return ResponseEntity.ok(DanhGiaQuanTriResponse.from(
                danhGiaService.doiTrangThai(id, TrangThaiDanhGia.DA_AN)));
    }

    /**
     * Shop tra loi cong khai duoi mot danh gia. Goi lan hai la sua noi dung, khong tao
     * them dong: phan hoi luu thang tren dong danh gia nen "moi danh gia toi da mot phan
     * hoi" la dieu khong the vi pham.
     */
    @PostMapping("{id}/phan-hoi")
    public ResponseEntity<DanhGiaQuanTriResponse> phanHoi(@PathVariable Long id,
                                                          @RequestBody PhanHoiShopBo yeuCau) {
        NguoiDung quanTri = nguoiDungQuanTriHienTai();
        return ResponseEntity.ok(DanhGiaQuanTriResponse.from(danhGiaService.datPhanHoiShop(
                id, yeuCau == null ? null : yeuCau.getNoiDung(), quanTri.getMaNguoiDung())));
    }

    private NguoiDung nguoiDungQuanTriHienTai() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        NguoiDung nguoiDung = authentication == null ? null
                : nguoiDungRepository.findByTenDangNhap(authentication.getName());
        if (nguoiDung == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập.");
        }
        return nguoiDung;
    }

    /** Tinh lai toan bo du lieu tong hop khi da lech. Idempotent. */
    @PostMapping("tinh-lai-tat-ca")
    public ResponseEntity<Integer> tinhLaiTatCa() {
        return ResponseEntity.ok(danhGiaService.tinhLaiTongHopTatCa());
    }


}
