package com.example.book_be.controller.admin;


import com.example.book_be.bo.SachBo;
import com.example.book_be.bo.UserBo;
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
@RequestMapping("/api/admin/sach")
public class SachController {

    @Autowired
    private SachService sachService;

    @Autowired
    private HinhAnhRepository hinhAnhRepository;

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


}
