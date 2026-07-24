package com.example.book_be.thongke.web.admin;

import com.example.book_be.thongke.service.ThongKeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller exposing admin dashboard statistics endpoints.
 * All endpoints require ADMIN role (enforced via SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/thong-ke")
public class ThongKeController {

    @Autowired
    private ThongKeService thongKeService;

    /**
     * GET /api/admin/thong-ke
     * Returns aggregated dashboard statistics: revenue, orders, users, top books.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getThongKe() {
        Map<String, Object> thongKe = thongKeService.getThongKe();
        return new ResponseEntity<>(thongKe, HttpStatus.OK);
    }
}
