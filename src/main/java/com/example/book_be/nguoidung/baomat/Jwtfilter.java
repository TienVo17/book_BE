package com.example.book_be.nguoidung.baomat;

import com.example.book_be.nguoidung.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class Jwtfilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(Jwtfilter.class);

    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserService userDetailService;

    public Jwtfilter() {

    }

    /**
     * Malformed/expired/wrong-signature Bearer token phai fail closed: khong throw ra ngoai filter
     * (se thanh 500 khong xu ly), chi bo qua xac thuc va de Spring Security tu choi 401/403 nhu
     * request khong co token.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                String username = jwtService.extractUsername(token);
                if (username != null) {
                    UserDetails userDetails = userDetailService.loadUserByUsername(username);
                    if (jwtService.validateToken(token, userDetails)) {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            } catch (UsernameNotFoundException e) {
                SecurityContextHolder.clearContext();
                log.debug("event=jwt_rejected reason=user_not_found");
            } catch (RuntimeException e) {
                // Bao gom ExpiredJwtException, MalformedJwtException, SignatureException,
                // UnsupportedJwtException, IllegalArgumentException tu thu vien jjwt.
                SecurityContextHolder.clearContext();
                log.debug("event=jwt_rejected reason=invalid_token type={}", e.getClass().getSimpleName());
            }
        }

        filterChain.doFilter(request, response);
    }
}
