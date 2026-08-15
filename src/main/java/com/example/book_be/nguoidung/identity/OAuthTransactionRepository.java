package com.example.book_be.nguoidung.identity;

import java.time.Instant;
import java.util.Optional;

/**
 * Khong ke thua JpaRepository de tang service co the duoc test bang mot ban cai dat trong
 * bo nho, va de khong vo tinh mo ra cac thao tac hang loat tren bang chua bi mat cua luong
 * dang nhap. Ban cai dat JPA that nam o OAuthTransactionJpaRepository.
 */
public interface OAuthTransactionRepository {
    Optional<OAuthTransaction> findByStateHash(String stateHash);

    <S extends OAuthTransaction> S save(S entity);

    /**
     * Don rac cac luot dang nhap da het han. Transaction het han khong con gia tri bao mat
     * nao, nhung de lai thi bang chi lon dan.
     */
    int deleteExpired(Instant cutoff);
}
