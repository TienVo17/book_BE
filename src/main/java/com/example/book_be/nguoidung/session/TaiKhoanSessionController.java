package com.example.book_be.nguoidung.session;

import com.example.book_be.nguoidung.baomat.JwtService;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.service.TaiKhoanService;
import com.example.book_be.nguoidung.service.UserService;
import com.example.book_be.shared.security.JwtResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/tai-khoan")
public class TaiKhoanSessionController {
    private final RefreshSessionService refreshSessionService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final AuthCsrfTokenService csrfTokenService;
    private final JwtService jwtService;
    private final UserService userService;

    public TaiKhoanSessionController(RefreshSessionService refreshSessionService,
                                     RefreshCookieFactory refreshCookieFactory,
                                     AuthCsrfTokenService csrfTokenService,
                                     JwtService jwtService,
                                     UserService userService) {
        this.refreshSessionService = refreshSessionService;
        this.refreshCookieFactory = refreshCookieFactory;
        this.csrfTokenService = csrfTokenService;
        this.jwtService = jwtService;
        this.userService = userService;
    }

    @GetMapping("/csrf")
    public ResponseEntity<?> csrf() {
        if (!refreshSessionService.isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        String token = csrfTokenService.issueToken();
        ResponseCookie cookie = ResponseCookie.from(AuthCsrfTokenService.COOKIE_NAME, token)
                .path("/").secure(true).httpOnly(false).sameSite("Lax").build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("csrfToken", token));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request) {
        if (!refreshSessionService.isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        TaiKhoanService.AuthenticatedSession authenticated =
                refreshSessionService.rotateAndIssueAccessToken(
                        cookieValue(request, RefreshCookieFactory.COOKIE_NAME));
        RefreshSessionService.SessionGrant grant = authenticated.refreshGrant();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshCookieFactory.issue(grant.rawToken(), grant.rememberMe()).toString())
                .body(response(authenticated.accessToken(), authenticated.user()));
    }

    @PostMapping("/dang-xuat")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        if (!refreshSessionService.isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        try {
            refreshSessionService.revokeCurrent(cookieValue(request, RefreshCookieFactory.COOKIE_NAME));
        } catch (RefreshSessionException ignored) {
            // Logout is deliberately idempotent; clearing the browser cookie is authoritative.
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear().toString())
                .build();
    }

    @GetMapping("/phien")
    public ResponseEntity<?> session(Authentication authentication) {
        NguoiDung user = userService.findByUsername(authentication.getName());
        if (user == null || !Boolean.TRUE.equals(user.getDaKichHoat())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Map.of("principal", SessionPrincipal.from(user)));
    }

    private JwtResponse response(String accessToken, NguoiDung user) {
        return new JwtResponse(accessToken, jwtService.getExpirationSeconds(), SessionPrincipal.from(user));
    }

    private String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
