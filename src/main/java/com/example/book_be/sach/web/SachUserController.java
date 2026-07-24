package com.example.book_be.sach.web;

import com.example.book_be.sach.dto.SachBo;
import com.example.book_be.sach.dto.SachResponse;
import com.example.book_be.sach.repository.HinhAnhRepository;
import com.example.book_be.sach.domain.HinhAnh;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.sach.service.SachService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sach")
public class SachUserController {

    @Autowired
    private SachService sachService;

    @Autowired
    private HinhAnhRepository hinhAnhRepository;

    @GetMapping
    public ResponseEntity<Page<SachResponse>> findAll(
            @RequestParam("page") Integer page,
            @RequestParam(value = "tensach", required = false) String tenSach,
            @RequestParam(value = "maTheLoai", required = false, defaultValue = "0") Integer maTheLoai) {
        SachBo model = new SachBo();
        model.setPage(page);
        model.setPageSize(8);
        model.setIsAdmin(false);
        model.setTenSach(tenSach);
        model.setMaTheLoai(maTheLoai);
        Page<Sach> result = sachService.findAll(model);
        return new ResponseEntity<>(SachResponse.fromPage(result), HttpStatus.OK);
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

    @GetMapping("/search")
    public Page<SachResponse> searchBooks(@RequestParam("tensach") String tenSach,
                                          @RequestParam("page") int page,
                                          @RequestParam("size") int size) {
        return SachResponse.fromPage(sachService.findBookByName(tenSach, page, size));
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
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(SachResponse.from(sach));
    }
}
