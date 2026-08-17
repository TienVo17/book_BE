package com.example.book_be.nguoidung.identity;

import java.time.Instant;
import java.util.Optional;

/**
 * Khong ke thua JpaRepository, cung ly do voi OAuthTransactionRepository: service phai test
 * duoc bang mot ban cai dat trong bo nho, va bang nay chua ho so dang do cua nguoi dung nen
 * khong mo san cac thao tac hang loat. Ban cai dat JPA nam o OAuthSignupIntentJpaRepository.
 */
public interface OAuthSignupIntentRepository {
    Optional<OAuthSignupIntent> findByIntentHash(String intentHash);

    <S extends OAuthSignupIntent> S save(S entity);

    /** Don rac cac ho so dang ky bo do. Het han thi khong con dung duoc, chi lam bang lon dan. */
    int deleteExpired(Instant cutoff);
}
