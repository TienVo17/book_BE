package com.example.book_be.nguoidung.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** exported=false: bang nay chua bi mat cua luong dang nhap, khong duoc lo qua Data REST. */
@RepositoryRestResource(exported = false)
public interface OAuthTransactionJpaRepository
        extends JpaRepository<OAuthTransaction, Long>, OAuthTransactionRepository {

    @Override
    @Modifying
    @Transactional
    @Query("DELETE FROM OAuthTransaction t WHERE t.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
