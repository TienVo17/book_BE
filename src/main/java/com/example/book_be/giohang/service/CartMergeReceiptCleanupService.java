package com.example.book_be.giohang.service;

import com.example.book_be.giohang.repository.GioHangMergeReceiptRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CartMergeReceiptCleanupService {
    static final int RETENTION_DAYS = 30;
    static final int DELETE_BATCH_SIZE = 500;

    private final GioHangMergeReceiptRepository mergeReceiptRepository;
    private final TransactionTemplate transactionTemplate;

    public CartMergeReceiptCleanupService(
            GioHangMergeReceiptRepository mergeReceiptRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.mergeReceiptRepository = mergeReceiptRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "${app.cart.merge-receipt-cleanup-cron:0 23 3 * * *}")
    public void deleteExpiredReceipts() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        while (deleteBatch(cutoff) == DELETE_BATCH_SIZE) {
            // Each batch commits independently to bound locks and undo-log growth.
        }
    }

    private int deleteBatch(LocalDateTime cutoff) {
        Integer deleted = transactionTemplate.execute(status -> {
            List<Long> ids = mergeReceiptRepository.findExpiredIds(
                    cutoff, DELETE_BATCH_SIZE);
            if (ids.isEmpty()) {
                return 0;
            }
            return mergeReceiptRepository.deleteByIds(ids);
        });
        return deleted == null ? 0 : deleted;
    }
}
