package com.example.book_be.security;

import com.example.book_be.services.JWT.Jwtfilter;
import com.example.book_be.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Arrays;

@Configuration
public class SecurityConfiguration {
    @Autowired
    private Jwtfilter jwtFilter;

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Autowired
    public DaoAuthenticationProvider authenticationProvider(UserService userService) {
        DaoAuthenticationProvider dap = new DaoAuthenticationProvider();
        dap.setUserDetailsService(userService);
        dap.setPasswordEncoder(bCryptPasswordEncoder());
        return dap;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(config -> config
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                .requestMatchers(HttpMethod.GET, Endpoints.PUBLIC_GET_ENDPOINS).permitAll()
                .requestMatchers(HttpMethod.POST, Endpoints.PUBLIC_POST_ENDPOINS).permitAll()
                .requestMatchers(HttpMethod.PUT, Endpoints.PUBLIC_PUT_ENDPOINS).permitAll()
                .requestMatchers(HttpMethod.DELETE, Endpoints.PUBLIC_DELETE_ENDPOINS).permitAll()

                .requestMatchers(HttpMethod.GET, Endpoints.ADMIN_GET_ENDPOINS).hasAuthority("ADMIN")
                .requestMatchers(HttpMethod.POST, Endpoints.ADMIN_POST_ENDPOINS).hasAuthority("ADMIN")
                .requestMatchers(HttpMethod.PUT, Endpoints.ADMIN_PUT_ENDPOINS).hasAuthority("ADMIN")
                .requestMatchers(HttpMethod.DELETE, Endpoints.ADMIN_DELETE_ENDPOINS).hasAuthority("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/admin/sach/insert").hasAuthority("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/admin/sach/update/**").hasAuthority("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/admin/sach/findImage/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/admin/quyen/findAll").hasAuthority("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/don-hang/cap-nhat-trang-thai-giao-hang/**").hasAuthority("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/admin/user/phan-quyen").hasAuthority("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/admin/sach/active/**").hasAuthority("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/admin/sach/unactive/**").hasAuthority("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/admin/danh-gia/findAll**").hasAuthority("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/admin/danh-gia/active/**").hasAuthority("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/admin/danh-gia/unactive/**").hasAuthority("ADMIN")
                .requestMatchers("/api/admin/**").hasAuthority("ADMIN")

                .requestMatchers(HttpMethod.GET, Endpoints.AUTH_GET_ENDPOINTS).authenticated()
                .requestMatchers(HttpMethod.POST, Endpoints.AUTH_POST_ENDPOINTS).authenticated()
                .requestMatchers(HttpMethod.PUT, Endpoints.AUTH_PUT_ENDPOINTS).authenticated()
                .requestMatchers(HttpMethod.DELETE, Endpoints.AUTH_DELETE_ENDPOINTS).authenticated()
                .requestMatchers(HttpMethod.POST, "/api/don-hang/them").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/don-hang/submitOrder**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/don-hang/vnpay-payment").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/don-hang/findAll**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/danh-gia/findAll**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/danh-gia/them-danh-gia-v1").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/sach**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/don-hang/them-don-hang-moi").permitAll()
        );

        http.cors(cors -> cors.configurationSource(request -> {
            CorsConfiguration corsConfig = new CorsConfiguration();
            corsConfig.addAllowedOrigin("http://localhost:3000");
            corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            corsConfig.addAllowedHeader("*");
            corsConfig.setAllowCredentials(true);
            corsConfig.setMaxAge(3600L);
            return corsConfig;
        }));

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        http.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
