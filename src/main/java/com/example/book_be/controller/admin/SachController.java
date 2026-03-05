package com.example.book_be.controller.admin;

import com.example.book_be.bo.SachBo;
import com.example.book_be.dao.HinhAnhRepository;
import com.example.book_be.dao.SachRepository;
import com.example.book_be.entity.HinhAnh;
import com.example.book_be.entity.Sach;
import com.example.book_be.services.CloudinaryService;
import com.example.book_be.services.admin.SachService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "http://localhost:3000/")
@RequestMapping("/api/admin/sach")
public class SachController {

    @Autowired
    private SachService sachService;

    @Autowired
    private HinhAnhRepository hinhAnhRepository;

    @Autowired
    private SachRepository sachRepository;

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

    /**
     * POST /api/admin/sach/{id}/hinh-anh
     * Upload one or more images for a book via Cloudinary.
     * Max file size: 5MB per file. Only image MIME types accepted.
     */
    @PostMapping("/{id}/hinh-anh")
    public ResponseEntity<?> uploadHinhAnh(@PathVariable Long id,
                                            @RequestParam("files") MultipartFile[] files) {
        if (cloudinaryService == null || !cloudinaryService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Cloudinary chua duoc cau hinh. Set CLOUDINARY_URL env var."));
        }

        Sach sach = sachRepository.findById(id).orElse(null);
        if (sach == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Khong tim thay sach voi id: " + id));
        }

        List<HinhAnh> savedImages = new ArrayList<>();
        for (MultipartFile file : files) {
            // Validate file size: max 5MB
            if (file.getSize() > 5 * 1024 * 1024) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "File " + file.getOriginalFilename() + " vuot qua 5MB"));
            }
            try {
                String url = cloudinaryService.upload(file);
                HinhAnh hinhAnh = new HinhAnh();
                hinhAnh.setTenHinhAnh("img_" + sach.getTenSach() + "_" + file.getOriginalFilename());
                hinhAnh.setUrlHinh(url);
                hinhAnh.setIcon(false);
                hinhAnh.setSach(sach);
                savedImages.add(hinhAnhRepository.save(hinhAnh));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", e.getMessage()));
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Upload that bai: " + e.getMessage()));
            }
        }

        return ResponseEntity.ok(savedImages);
    }
}
