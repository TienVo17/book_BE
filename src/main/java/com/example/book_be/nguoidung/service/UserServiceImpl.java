package com.example.book_be.nguoidung.service;

import com.example.book_be.nguoidung.repository.NguoiDungRepository;
import com.example.book_be.nguoidung.repository.QuyenRepository;
import com.example.book_be.nguoidung.domain.NguoiDung;
import com.example.book_be.nguoidung.domain.Quyen;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {
    private NguoiDungRepository nguoiDungRepository;

    private QuyenRepository quyenRepository;

    @Autowired
    public UserServiceImpl(NguoiDungRepository nguoiDungRepository, QuyenRepository quyenRepository) {
        this.nguoiDungRepository = nguoiDungRepository;
        this.quyenRepository = quyenRepository;
    }

    @Override
    public NguoiDung findByUsername(String tenDangNhap) {
        return nguoiDungRepository.findByTenDangNhap(tenDangNhap);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        NguoiDung nguoiDung = nguoiDungRepository.findByTenDangNhap(username);

        if (nguoiDung == null) {
            throw new UsernameNotFoundException("Tai khoan khong ton tai: " + username);
        }

        // Tai khoan chua kich hoat (hoac da bi vo hieu hoa boi V10) phai bi chan ngay tai tang
        // xac thuc: chi dat da_kich_hoat=0 trong DB la khong du neu UserDetails van bao enabled.
        return User.withUsername(nguoiDung.getTenDangNhap())
                .password(nguoiDung.getMatKhau())
                .authorities(rolesToAuthorities(nguoiDung.getDanhSachQuyen()))
                .disabled(!Boolean.TRUE.equals(nguoiDung.getDaKichHoat()))
                .build();
    }

    private Collection<? extends GrantedAuthority> rolesToAuthorities(Collection<Quyen> quyens) {
        return quyens.stream()
                .map(quyen -> new SimpleGrantedAuthority(quyen.getTenQuyen()))
                .collect(Collectors.toList());
    }
}
