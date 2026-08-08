package com.example.book_be.nhantin.web;

import com.example.book_be.nhantin.service.NhanTinService;
import com.example.book_be.shared.security.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/nhan-tin")
public class NhanTinController {

    private static final int DANG_KY_TOI_DA = 5;
    private static final Duration DANG_KY_CUA_SO = Duration.ofMinutes(10);

    private final NhanTinService nhanTinService;
    private final RateLimiter rateLimiter;

    public NhanTinController(NhanTinService nhanTinService, RateLimiter rateLimiter) {
        this.nhanTinService = nhanTinService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Endpoint nay cong khai va ghi vao database, nen phai co gioi han tan suat: neu khong,
     * bat ky ai cung bom duoc bang nay day dia chi rac bang mot vong lap.
     */
    @PostMapping("/dang-ky")
    public ResponseEntity<Map<String, Object>> dangKy(@RequestBody DangKyNhanTinRequest request,
                                                      HttpServletRequest httpRequest) {
        batBuocTrongGioiHan("nhan-tin:" + diaChiIp(httpRequest));
        nhanTinService.dangKy(request == null ? null : request.getEmail());
        return ResponseEntity.ok(Map.of("daDangKy", true));
    }

    @PostMapping("/xac-nhan/{maXacNhan}")
    public ResponseEntity<Map<String, Object>> xacNhan(@PathVariable String maXacNhan,
                                                       HttpServletRequest httpRequest) {
        batBuocTrongGioiHan("nhan-tin-xac-nhan:" + diaChiIp(httpRequest));
        nhanTinService.xacNhan(maXacNhan);
        return ResponseEntity.ok(Map.of("daXacNhan", true));
    }

    @PostMapping("/huy/{maHuy}")
    public ResponseEntity<Map<String, Object>> huy(@PathVariable String maHuy,
                                                   HttpServletRequest httpRequest) {
        batBuocTrongGioiHan("nhan-tin-huy:" + diaChiIp(httpRequest));
        nhanTinService.huy(maHuy);
        return ResponseEntity.ok(Map.of("daHuy", true));
    }

    private void batBuocTrongGioiHan(String khoa) {
        if (!rateLimiter.choPhep(khoa, DANG_KY_TOI_DA, DANG_KY_CUA_SO)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn đã thao tác quá nhiều lần. Vui lòng thử lại sau.");
        }
    }

    private String diaChiIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
