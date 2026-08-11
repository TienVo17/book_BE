package com.example.book_be.nguoidung.repository;

import com.example.book_be.nguoidung.domain.NguoiDung;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Optional;

@RepositoryRestResource(path = "nguoi-dung")
public interface NguoiDungRepository extends JpaRepository<NguoiDung, Long>, JpaSpecificationExecutor {
    boolean existsByTenDangNhap(String tenDangNhap);

    boolean existsByEmail(String email);

    NguoiDung findByTenDangNhap(String tenDangNhap);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM NguoiDung u WHERE u.tenDangNhap = :tenDangNhap")
    Optional<NguoiDung> findByTenDangNhapForCartWrite(
            @Param("tenDangNhap") String tenDangNhap
    );

    NguoiDung findByEmail(String email);

    NguoiDung findByResetPasswordToken(String resetPasswordToken);
}
