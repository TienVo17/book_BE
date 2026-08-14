package com.example.book_be.nguoidung.session;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RepositoryRestResource(exported = false)
public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, Long> {
    @Query("SELECT session.nguoiDung.maNguoiDung FROM RefreshTokenSession session "
            + "WHERE session.selector = :selector")
    Optional<Integer> findUserIdBySelector(@Param("selector") String selector);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT session FROM RefreshTokenSession session JOIN FETCH session.nguoiDung "
            + "WHERE session.selector = :selector")
    Optional<RefreshTokenSession> findBySelectorForUpdate(@Param("selector") String selector);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT session FROM RefreshTokenSession session "
            + "WHERE session.familyId = :familyId ORDER BY session.id")
    List<RefreshTokenSession> findFamilyForUpdate(@Param("familyId") String familyId);

    @Modifying
    @Query("UPDATE RefreshTokenSession session SET session.revokedAt = :revokedAt "
            + "WHERE session.nguoiDung.maNguoiDung = :maNguoiDung AND session.revokedAt IS NULL")
    int revokeAllByUser(@Param("maNguoiDung") int maNguoiDung,
                        @Param("revokedAt") Instant revokedAt);
}
