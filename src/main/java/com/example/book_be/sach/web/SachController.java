package com.example.book_be.sach.web;

import com.example.book_be.sach.dto.SachAdminUpsertBo;
import com.example.book_be.sach.dto.SachBo;
import com.example.book_be.sach.dto.SachTonKhoDieuChinhRequest;
import com.example.book_be.sach.dto.SachTonKhoResponse;
import com.example.book_be.sach.service.SachNotFoundException;
import com.example.book_be.sach.service.StockAdjustmentConflictException;
import com.example.book_be.sach.repository.HinhAnhRepository;
import com.example.book_be.sach.repository.SachRepository;
import com.example.book_be.sach.domain.HinhAnh;
import com.example.book_be.sach.domain.Sach;
import com.example.book_be.sach.service.CloudinaryService;
import com.example.book_be.sach.service.BookImageStorageService;
import com.example.book_be.sach.service.SachService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/sach")
public class SachController {

    @Autowired
    private SachService sachService;

    @Autowired
    private HinhAnhRepository hinhAnhRepository;

    @Autowired
    private SachRepository sachRepository;

    @Autowired
    private BookImageStorageService bookImageStorageService;

    @Autowired(required = false)
    private CloudinaryService cloudinaryService;

    @GetMapping
    public ResponseEntity<Page<Sach>> findAll(@RequestParam("page") Integer page) {
        SachBo model = new SachBo();
        model.setPage(page);
        model.setPageSize(10);
        model.setIsAdmin(Boolean.TRUE);
        Page<Sach> result = sachService.findAll(model);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @PostMapping("insert")
    public ResponseEntity<?> themSach(@RequestBody SachAdminUpsertBo bo) {
        return new ResponseEntity<>(sachService.save(bo), HttpStatus.OK);
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
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody SachAdminUpsertBo bo) throws Exception {
        bo.setMaSach(Math.toIntExact(id));
        Sach sach = sachService.update(bo);
        return new ResponseEntity<>(sach, HttpStatus.OK);
    }

    @PatchMapping("/{id}/ton-kho")
    public ResponseEntity<SachTonKhoResponse> dieuChinhTonKho(
            @PathVariable Long id,
            @RequestBody SachTonKhoDieuChinhRequest request) {
        return ResponseEntity.ok(sachService.dieuChinhTonKho(id,
                request == null ? null : request.getSoLuongThayDoi()));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Sach> delete(@PathVariable Long id) {
        Sach sach = sachService.delete(id);
        return new ResponseEntity<>(sach, HttpStatus.OK);
    }

    @GetMapping("{id}")
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

    @PostMapping("/{id}/hinh-anh")
    public ResponseEntity<?> uploadHinhAnh(@PathVariable Long id, @RequestParam("files") MultipartFile[] files)
            throws IOException {
        if (cloudinaryService == null || !cloudinaryService.isConfigured()) {
            throw new IllegalStateException("Cloudinary chưa được cấu hình.");
        }

        Sach sach = sachRepository.findById(id)
                .orElseThrow(() -> new SachNotFoundException(id));
        return ResponseEntity.ok(bookImageStorageService.saveUploadedImages(sach, files));
    }

    @PostMapping("/migrate-hinh-anh-base64")
    public ResponseEntity<?> migrateBase64Images(@RequestParam(name = "limit", defaultValue = "20") Integer limit)
            throws IOException {
        return ResponseEntity.ok(bookImageStorageService.migrateLegacyImages(limit));
    }
}
