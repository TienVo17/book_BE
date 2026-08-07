package com.example.book_be.sach.web;

import com.example.book_be.sach.dto.SachBo;
import com.example.book_be.sach.dto.SachGoiYResponse;
import com.example.book_be.sach.dto.SachResponse;
import com.example.book_be.sach.repository.HinhAnhRepository;
import com.example.book_be.sach.domain.HinhAnh;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.sach.service.SachService;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/sach")
public class SachUserController {

    private static final int DEFAULT_PAGE_SIZE = 8;
    private static final int MAX_PAGE_SIZE = 48;
    private static final int DEFAULT_GOI_Y_LIMIT = 8;
    private static final int MAX_GOI_Y_LIMIT = 10;
    private static final Set<String> SORT_HOP_LE = Set.of(
            SachService.SORT_MOI_NHAT,
            SachService.SORT_GIA_TANG,
            SachService.SORT_GIA_GIAM,
            SachService.SORT_TEN_AZ,
            SachService.SORT_DANH_GIA);

    @Autowired
    private SachService sachService;

    @Autowired
    private HinhAnhRepository hinhAnhRepository;

    @GetMapping
    public ResponseEntity<Page<SachResponse>> findAll(
            @RequestParam("page") Integer page,
            @RequestParam(value = "tensach", required = false) String tenSach,
            @RequestParam(value = "maTheLoai", required = false, defaultValue = "0") Integer maTheLoai,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "giaMin", required = false) Double giaMin,
            @RequestParam(value = "giaMax", required = false) Double giaMax,
            @RequestParam(value = "sort", required = false) String sort) {
        if (giaMin != null && giaMax != null && giaMin > giaMax) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Giá tối thiểu không được lớn hơn giá tối đa.");
        }
        String sortValue = sort == null || sort.isBlank() ? SachService.SORT_MOI_NHAT : sort.trim();
        if (!SORT_HOP_LE.contains(sortValue)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Giá trị sắp xếp không hợp lệ: " + sortValue);
        }

        SachBo model = new SachBo();
        model.setPage(page);
        model.setPageSize(clampKichThuocTrang(size));
        model.setIsAdmin(false);
        model.setTenSach(tenSach);
        model.setMaTheLoai(maTheLoai);
        model.setGiaMin(giaMin);
        model.setGiaMax(giaMax);
        model.setSort(sortValue);
        Page<Sach> result = sachService.findAll(model);
        return new ResponseEntity<>(SachResponse.fromPage(result), HttpStatus.OK);
    }

    /** Goi y khi go tim kiem: bo qua ngay tu 0-1 ky tu de khong dot database cho moi ky go. */
    @GetMapping("/goi-y")
    public ResponseEntity<List<SachGoiYResponse>> goiY(
            @RequestParam("q") String q,
            @RequestParam(value = "limit", required = false) Integer limit) {
        String tuKhoa = q == null ? "" : q.trim();
        if (tuKhoa.length() < 2) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(sachService.findGoiY(tuKhoa, clampGoiYLimit(limit)));
    }

    private int clampKichThuocTrang(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.max(1, Math.min(MAX_PAGE_SIZE, size));
    }

    private int clampGoiYLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_GOI_Y_LIMIT;
        }
        return Math.max(1, Math.min(MAX_GOI_Y_LIMIT, limit));
    }

    // Restrict to numeric IDs only to avoid conflict with /ban-chay, /moi-nhat, /slug etc.
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<SachResponse> findById(@PathVariable Long id) {
        Sach sach = sachService.findById(id);
        return new ResponseEntity<>(SachResponse.from(sach), HttpStatus.OK);
    }

    @GetMapping("findImage/{maSach}")
    public List<HinhAnh> findImage(@PathVariable Long maSach) {
        return hinhAnhRepository.findAll((root, query, criteriaBuilder) -> criteriaBuilder.equal(
                root.get("sach").get("maSach"), maSach
        ));
    }

    // Best-selling books
    @GetMapping("/ban-chay")
    public ResponseEntity<List<SachResponse>> findBanChay(
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(SachResponse.fromList(sachService.findBanChay(limit)));
    }

    // Newest books
    @GetMapping("/moi-nhat")
    public ResponseEntity<List<SachResponse>> findMoiNhat(
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(SachResponse.fromList(sachService.findMoiNhat(limit)));
    }

    // Related books by book ID
    @GetMapping("/{id:\\d+}/lien-quan")
    public ResponseEntity<List<SachResponse>> findLienQuan(
            @PathVariable int id,
            @RequestParam(value = "limit", defaultValue = "6") int limit) {
        return ResponseEntity.ok(SachResponse.fromList(sachService.findLienQuan(id, limit)));
    }

    // Lookup book by slug
    @GetMapping("/slug/{slug}")
    public ResponseEntity<SachResponse> findBySlug(@PathVariable String slug) {
        Sach sach = sachService.findBySlug(slug);
        if (sach == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sách không tồn tại.");
        }
        return ResponseEntity.ok(SachResponse.from(sach));
    }
}
