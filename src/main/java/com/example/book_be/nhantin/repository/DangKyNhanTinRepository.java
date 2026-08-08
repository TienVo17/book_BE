package com.example.book_be.nhantin.repository;

import com.example.book_be.nhantin.domain.DangKyNhanTin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;

import java.util.Optional;

/**
 * {@code exported = false} la bat buoc, khong phai tuy chon.
 *
 * <p>Spring Data REST tu phoi moi repository duoc export ra {@code /dangKyNhanTins} kem ca
 * cac query method duoi {@code /search/...}. Voi bang nay do se la mot danh sach email cong
 * khai — dung thu vua phai dong lai o SachRepository sau khi phat hien
 * {@code /sach/search/findAllByIsActive} liet ke duoc sach da an.
 */
@RepositoryRestResource(exported = false)
public interface DangKyNhanTinRepository extends JpaRepository<DangKyNhanTin, Long> {

    @RestResource(exported = false)
    Optional<DangKyNhanTin> findByEmail(String email);

    @RestResource(exported = false)
    Optional<DangKyNhanTin> findByMaHuy(String maHuy);

    @RestResource(exported = false)
    Optional<DangKyNhanTin> findByMaXacNhan(String maXacNhan);
}
