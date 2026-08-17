package com.example.book_be.nguoidung.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** exported=false: bang nay chua ho so dang ky dang do, khong duoc lo qua Data REST. */
@RepositoryRestResource(exported = false)
public interface OAuthSignupIntentJpaRepository
        extends JpaRepository<OAuthSignupIntent, Long>, OAuthSignupIntentRepository {

    @Override
    @Modifying
    @Transactional
    @Query("DELETE FROM OAuthSignupIntent i WHERE i.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
