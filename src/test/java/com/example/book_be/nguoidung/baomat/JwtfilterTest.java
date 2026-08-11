package com.example.book_be.nguoidung.baomat;

import com.example.book_be.nguoidung.service.UserService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtfilterTest {

    @Mock JwtService jwtService;
    @Mock UserService userService;
    @Mock FilterChain filterChain;
    @InjectMocks Jwtfilter jwtfilter;

    @Test
    void jwt_con_han_cua_tai_khoan_bi_vo_hieu_hoa_khong_duoc_xac_thuc()
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer stale-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        UserDetails disabledUser = User.withUsername("disabled-user")
                .password("unused")
                .authorities("USER")
                .disabled(true)
                .build();
        when(jwtService.extractUsername("stale-token"))
                .thenReturn("disabled-user");
        when(userService.loadUserByUsername("disabled-user"))
                .thenReturn(disabledUser);

        jwtfilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).validateToken("stale-token", disabledUser);
        verify(filterChain).doFilter(request, response);
        SecurityContextHolder.clearContext();
    }
}
