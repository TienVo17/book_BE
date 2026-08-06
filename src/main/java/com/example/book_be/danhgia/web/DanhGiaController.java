package com.example.book_be.danhgia.web;

import com.example.book_be.danhgia.dto.CoTheDanhGiaResponse;
import com.example.book_be.danhgia.dto.DanhGiaBo;
import com.example.book_be.danhgia.dto.DanhGiaCongKhaiResponse;
import com.example.book_be.danhgia.dto.DanhGiaTrangResponse;
import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.danhgia.domain.DanhGiaHinhAnh;
import com.example.book_be.danhgia.domain.SuDanhGia;
import com.example.book_be.danhgia.service.DanhGiaDocService;
import com.example.book_be.danhgia.service.DanhGiaHinhAnhService;
import com.example.book_be.danhgia.service.DanhGiaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("api/danh-gia")
public class DanhGiaController {
    @Autowired
    private DanhGiaService danhGiaService;

    @Autowired
    private DanhGiaDocService danhGiaDocService;

    @Autowired
    private DanhGiaHinhAnhService danhGiaHinhAnhService;

    @Autowired
    private NguoiDungRepository nguoiDungRepository;

    /**
     * Mot request du cho ca khoi danh gia tren trang san pham.
     *
     * <p>Thay cho {@code findAll} cu, von tra ve toan bo danh gia cua mot cuon trong mot
     * mang khong gioi han va khong kem thong tin tong hop nao.
     *
     * <p>Cong khai: khach chua dang nhap van doc duoc. {@code maNguoiDungDangXem} chi phuc
     * vu co {@code laCuaToi}, nen khong dang nhap khong lam mat noi dung gi.
     */
    @GetMapping
    public DanhGiaTrangResponse docTrang(@RequestParam("maSach") Integer maSach,
                                         @RequestParam(value = "page", required = false) Integer page,
                                         @RequestParam(value = "size", required = false) Integer size,
                                         @RequestParam(value = "sort", required = false) String sort,
                                         @RequestParam(value = "loc", required = false) Integer loc) {
        if (maSach == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu mã sách.");
        }
        if (loc != null && (loc < 1 || loc > 5)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bộ lọc sao không hợp lệ.");
        }
        return danhGiaDocService.docTrang(maSach, page, size, sort, loc, maNguoiDungDangXemHoacNull());
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

    /**
     * Bat/tat binh chon "huu ich". Bam lan hai la go binh chon, nhu nut thich o moi noi khac.
     *
     * <p>Tra ve so luot con lai de giao dien khong phai tai lai ca trang chi de cap nhat
     * mot con so.
     */
    @PostMapping("/{maDanhGia}/huu-ich")
    public Map<String, Object> doiBinhChonHuuIch(@PathVariable Long maDanhGia) {
        NguoiDung nguoiDung = nguoiDungHienTai();
        long soLuot = danhGiaService.doiBinhChonHuuIch(maDanhGia, nguoiDung.getMaNguoiDung());
        return Map.of("maDanhGia", maDanhGia, "soLuotHuuIch", soLuot);
    }

    /**
     * Dinh kem mot anh vao danh gia cua chinh minh.
     *
     * <p>Moi rang buoc (so luong, kich thuoc, dinh dang, han ngach, tan suat) deu cuong
     * che o service — endpoint nay khong tu kiem tra gi de khoi co hai ban quy tac.
     */
    @PostMapping("/{maDanhGia}/hinh-anh")
    public Map<String, Object> themAnh(@PathVariable Long maDanhGia,
                                       @RequestHeader(value = "Idempotency-Key", required = false)
                                       String idempotencyKey,
                                       @RequestParam("tep") MultipartFile tep) throws IOException {
        NguoiDung nguoiDung = nguoiDungHienTai();
        DanhGiaHinhAnh anh = danhGiaHinhAnhService.themAnh(
                maDanhGia, nguoiDung.getMaNguoiDung(), idempotencyKey, tep);
        return Map.of("maHinhAnh", anh.getMaHinhAnh(), "urlHinh", anh.getUrlHinh());
    }

    /** Chu so huu go anh cua minh; ADMIN go anh vi pham. */
    @PostMapping("/hinh-anh/{maHinhAnh}/xoa")
    public Map<String, Object> xoaAnh(@PathVariable Long maHinhAnh) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        NguoiDung nguoiDung = nguoiDungHienTai();
        boolean laQuanTri = authentication.getAuthorities().stream()
                .anyMatch(quyen -> "ADMIN".equals(quyen.getAuthority()));
        danhGiaHinhAnhService.xoaAnh(maHinhAnh, nguoiDung.getMaNguoiDung(), laQuanTri);
        return Map.of("maHinhAnh", maHinhAnh, "daXoa", true);
    }

    @PostMapping("/them-danh-gia-v1")
    public DanhGiaCongKhaiResponse addReview(@RequestBody DanhGiaBo danhGia) {
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
        return DanhGiaCongKhaiResponse.from(suDanhGia, nguoiDung.getMaNguoiDung());
    }

    @PostMapping("/sua-danh-gia/{maDanhGia}")
    public DanhGiaCongKhaiResponse updateReview(@PathVariable Long maDanhGia, @RequestBody SuDanhGia danhGia) {
        NguoiDung nguoiDung = nguoiDungHienTai();
        return DanhGiaCongKhaiResponse.from(danhGiaService.updateReview(
                maDanhGia, danhGia, (long) nguoiDung.getMaNguoiDung()
        ), nguoiDung.getMaNguoiDung());
    }

    @PostMapping("/xoa-danh-gia/{maDanhGia}")
    public DanhGiaCongKhaiResponse deleteReview(@PathVariable Long maDanhGia) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        NguoiDung nguoiDung = nguoiDungHienTai();
        boolean laQuanTri = authentication.getAuthorities().stream()
                .anyMatch(quyen -> "ADMIN".equals(quyen.getAuthority()));
        return DanhGiaCongKhaiResponse.from(danhGiaService.deleteReview(
                maDanhGia, (long) nguoiDung.getMaNguoiDung(), laQuanTri
        ), nguoiDung.getMaNguoiDung());
    }

    /**
     * Nguoi dung hien tai neu co, {@code null} neu la khach. Khac
     * {@link #nguoiDungHienTai()}: duong doc cong khai khong duoc ném 401 chi vi chua
     * dang nhap — no chi mat co {@code laCuaToi}.
     */
    private Integer maNguoiDungDangXemHoacNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        NguoiDung nguoiDung = nguoiDungRepository.findByTenDangNhap(authentication.getName());
        return nguoiDung == null ? null : nguoiDung.getMaNguoiDung();
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
