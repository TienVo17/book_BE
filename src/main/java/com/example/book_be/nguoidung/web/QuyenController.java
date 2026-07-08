package com.example.book_be.nguoidung.web;

import com.example.book_be.nguoidung.dto.PhanQuyenBo;
import com.example.book_be.nguoidung.dto.UserBo;
import com.example.book_be.nguoidung.repository.QuyenRepository;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.domain.Quyen;
import com.example.book_be.nguoidung.service.AdminUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "http://localhost:3000/")
@RequestMapping("api/admin/quyen")
public class QuyenController {
    @Autowired
    private QuyenRepository quyenRepository;

    @GetMapping("findAll")
    public List<Quyen> findAll() {
        return quyenRepository.findAll();
    }


}
