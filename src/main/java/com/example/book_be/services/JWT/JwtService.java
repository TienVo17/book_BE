package com.example.book_be.services.JWT;

import com.example.book_be.entity.NguoiDung;
import com.example.book_be.entity.Quyen;
import com.example.book_be.services.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.*;
import java.util.function.Function;

@Component
public class JwtService {
    public static final String SECRET = "U29tZVZlcnlMb25nU2VjdXJlS2V5VGhhdFNhdGlzZmllczMyQnl0ZXM=";

    @Autowired
    private UserService userService;

    // tạo JWT dựa trên tên đăng nhập
    public String generateToken(String tenDangNhap) {
        Map<String, Object> claims = new HashMap<>();
        NguoiDung nguoiDung = userService.findByUsername(tenDangNhap);
        boolean isAdmin = false;
        boolean isStaff = false;
        boolean isUser = false;
        if (nguoiDung != null && nguoiDung.getDanhSachQuyen().size() > 0) {
            List<Quyen> list = nguoiDung.getDanhSachQuyen();
            for (Quyen q : list) {
                if (q.getTenQuyen().equals("ADMIN")) {
                    isAdmin = true;
                }
                if (q.getTenQuyen().equals("STAFF")) {
                    isStaff = true;
                }
                if (q.getTenQuyen().equals("USER")) {
                    isUser = true;
                }
            }
        }
        claims.put("isAdmin", isAdmin);
        claims.put("isStaff", isStaff);
        claims.put("isUser", isUser);
        return createToken(claims, tenDangNhap);

    }

    private String createToken(Map<String, Object> claims, String tenDangNhap) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(tenDangNhap)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 30 * 60 * 1000)) //JTW hết hạn sau 30p
                .signWith(SignatureAlgorithm.HS256, getSigneKey())
                .compact();


    }

    // Lấy Serect key
    private Key getSigneKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // Trích xuất thông tin
    private Claims ExtractAllClaims(String token) {
        return Jwts.parser().setSigningKey(SECRET).parseClaimsJws(token).getBody();
    }

    //Trích xuất thông tin cho 1 claim
    public <T> T extractClaims(String token, Function<Claims, T> claimsTFunction) {
        final Claims claims = ExtractAllClaims(token);
        return claimsTFunction.apply(claims);


    }

    // Kiểm tra thoời gian hết hạn
    public Date extractExpiration(String token) {
        return extractClaims(token, Claims::getExpiration);
    }

    public String extractUsername(String token) {
        return extractClaims(token, Claims::getSubject);
    }

    // Kiểm tra cái JWT đã hết hạn
    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // Kiểm tra tính hợp lệ
    public Boolean validateToken(String token, UserDetails userDetails) {
        final String tenDangNhap = extractUsername(token);
        System.out.println(tenDangNhap);
        return (tenDangNhap.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
}
