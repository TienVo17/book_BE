package com.example.book_be.nguoidung.repository;

import com.example.book_be.nguoidung.domain.AdminBootstrapState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Optional;

/** exported=false: trang thai bootstrap la noi bo, khong duoc lo qua Spring Data REST. */
@RepositoryRestResource(exported = false)
public interface AdminBootstrapStateRepository extends JpaRepository<AdminBootstrapState, Integer> {

    /**
     * PESSIMISTIC_WRITE giu instance thu hai cho tai day cho den khi instance thu nhat commit,
     * nen kiem tra "da su dung chua" va viec tao admin nam trong cung mot vung loai tru.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select trangThai from AdminBootstrapState trangThai where trangThai.singletonId = :id")
    Optional<AdminBootstrapState> khoaDeGhi(@Param("id") Integer id);
}
