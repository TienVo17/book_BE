package com.example.book_be.nguoidung.baomat;

import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.domain.Quyen;
import com.example.book_be.nguoidung.service.UserService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServiceTest {
    @Test
    void access_token_keeps_compatibility_claims_and_adds_immutable_uid_for_fifteen_minutes() {
        UserService users = mock(UserService.class);
        NguoiDung user = new NguoiDung();
        user.setMaNguoiDung(42);
        user.setTenDangNhap("alice");
        Quyen role = new Quyen();
        role.setTenQuyen("USER");
        user.setDanhSachQuyen(List.of(role));
        when(users.findByUsername("alice")).thenReturn(user);

        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "userService", users);
        ReflectionTestUtils.setField(service, "secret", Base64.getEncoder().encodeToString(
                "test-jwt-signing-key-with-more-than-32-bytes".getBytes(StandardCharsets.UTF_8)));
        ReflectionTestUtils.setField(service, "expirationMs", 900_000L);

        String token = service.generateToken("alice");
        Claims claims = service.extractClaims(token, value -> value);

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get("uid", Integer.class)).isEqualTo(42);
        assertThat(claims.get("isUser", Boolean.class)).isTrue();
        assertThat(claims.get("isStaff", Boolean.class)).isFalse();
        assertThat(claims.get("isAdmin", Boolean.class)).isFalse();
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(900_000L);
    }
}
