package com.example.book_be.controller;

import com.example.book_be.bo.SachBo;
import com.example.book_be.dao.HinhAnhRepository;
import com.example.book_be.entity.HinhAnh;
import com.example.book_be.entity.Sach;
import com.example.book_be.services.admin.SachService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:3000/")
@RequestMapping("/api/sach")
public class SachUserController {

    @Autowired
    private SachService sachService;

    @Autowired
    private HinhAnhRepository hinhAnhRepository;

    @GetMapping
    public ResponseEntity<Page<Sach>> findAll(
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
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @PostMapping("insert")
    public ResponseEntity<?> dangKyNguoiDung(@RequestBody Sach sach) {
        return new ResponseEntity<>(sachService.save(sach), HttpStatus.OK);
    }

    @PostMapping("active/{id}")
    public ResponseEntity<?> active(@PathVariable Long id) {
        return new ResponseEntity<>(sachService.active(id), HttpStatus.OK);
    }

    @PostMapping("unactive/{id}")
    public ResponseEntity<?> unactive(@PathVariable Long id) {
        return new ResponseEntity<>(sachService.unactive(id), HttpStatus.OK);
    }

    @PutMapping("update/{id}")
    public ResponseEntity<Sach> update(@PathVariable Long id, @RequestBody Sach bo) throws Exception {
        Sach sach = sachService.update(bo);
        return new ResponseEntity<>(sach, HttpStatus.OK);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Sach> delete(@PathVariable Long id) {
        Sach sach = sachService.delete(id);
        return new ResponseEntity<>(sach, HttpStatus.OK);
    }

    // Restrict to numeric IDs only to avoid conflict with /ban-chay, /moi-nhat, /slug etc.
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<Sach> findById(@PathVariable Long id) {
        Sach sach = sachService.findById(id);
        return new ResponseEntity<>(sach, HttpStatus.OK);
    }

    @GetMapping("findImage/{maSach}")
    public List<HinhAnh> findImage(@PathVariable Long maSach) {
        return hinhAnhRepository.findAll((root, query, criteriaBuilder) -> criteriaBuilder.equal(
                root.get("sach").get("maSach"), maSach
        ));
    }

    @GetMapping("/search")
    public Page<Sach> searchBooks(@RequestParam("tensach") String tenSach,
                                  @RequestParam("page") int page,
                                  @RequestParam("size") int size) {
        return sachService.findBookByName(tenSach, page, size);
    }

    // Best-selling books
    @GetMapping("/ban-chay")
    public ResponseEntity<List<Sach>> findBanChay(
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(sachService.findBanChay(limit));
    }

    // Newest books
    @GetMapping("/moi-nhat")
    public ResponseEntity<List<Sach>> findMoiNhat(
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(sachService.findMoiNhat(limit));
    }

    // Related books by book ID
    @GetMapping("/{id:\\d+}/lien-quan")
    public ResponseEntity<List<Sach>> findLienQuan(
            @PathVariable int id,
            @RequestParam(value = "limit", defaultValue = "6") int limit) {
        return ResponseEntity.ok(sachService.findLienQuan(id, limit));
    }

    // Lookup book by slug
    @GetMapping("/slug/{slug}")
    public ResponseEntity<Sach> findBySlug(@PathVariable String slug) {
        Sach sach = sachService.findBySlug(slug);
        if (sach == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(sach);
    }
}
