package com.example.book_be.controller.admin;

import com.example.book_be.dao.ChiTietDonHangRepository;
import com.example.book_be.dao.DonHangRepository;
import com.example.book_be.dao.NguoiDungRepository;
import com.example.book_be.entity.ChiTietDonHang;
import com.example.book_be.entity.DonHang;
import com.example.book_be.entity.NguoiDung;
import com.example.book_be.entity.Sach;
import com.example.book_be.services.VNPayService;
import com.example.book_be.services.cart.OrderService;
import com.example.book_be.services.email.EmailService;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("api/admin/don-hang")
public class DonHangAdminController {


    @Autowired
    private OrderService orderService;

    @Autowired
    private VNPayService vnPayService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private DonHangRepository donHangRepository;

    @Autowired
    private ChiTietDonHangRepository chiTietDonHangRepository;

    @Autowired
    private NguoiDungRepository nguoiDungRepository;

    @GetMapping("/findAll")
    public Page<DonHang> findAll(@RequestParam("page") Integer page,HttpServletRequest request) {
        Pageable pageable = PageRequest.of(page, 10);
        NguoiDung nguoiDung = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication.getName() != null) {
            nguoiDung = nguoiDungRepository.findByTenDangNhap(authentication.getName());
        }
        NguoiDung finalNguoiDung = nguoiDung;
        return donHangRepository.findAll((root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("nguoiDung").get("maNguoiDung"), finalNguoiDung.getMaNguoiDung()));
            return builder.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }
}
