package com.example.book_be.controller.admin;

import com.example.book_be.bo.PhanQuyenBo;
import com.example.book_be.bo.UserBo;
import com.example.book_be.dao.QuyenRepository;
import com.example.book_be.entity.NguoiDung;
import com.example.book_be.entity.Quyen;
import com.example.book_be.services.admin.AdminUserService;
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
