package com.example.book_be.giohang.service;

import com.example.book_be.giohang.repository.GioHangMergeReceiptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartMergeReceiptCleanupServiceTest {

    @Mock
    GioHangMergeReceiptRepository mergeReceiptRepository;
    @Mock
    PlatformTransactionManager transactionManager;

    @Test
    void xoa_receipt_cu_hon_30_ngay_theo_batch_transaction_rieng() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(ignored -> new SimpleTransactionStatus());
        List<Long> firstBatch = java.util.stream.LongStream
                .rangeClosed(1, CartMergeReceiptCleanupService.DELETE_BATCH_SIZE)
                .boxed()
                .toList();
        when(mergeReceiptRepository.findExpiredIds(any(), anyInt()))
                .thenReturn(firstBatch, List.of(501L));
        when(mergeReceiptRepository.deleteByIds(firstBatch))
                .thenReturn(CartMergeReceiptCleanupService.DELETE_BATCH_SIZE);
        when(mergeReceiptRepository.deleteByIds(List.of(501L)))
                .thenReturn(1);
        CartMergeReceiptCleanupService service =
                new CartMergeReceiptCleanupService(
                        mergeReceiptRepository, transactionManager);
        LocalDateTime before = LocalDateTime.now()
                .minusDays(CartMergeReceiptCleanupService.RETENTION_DAYS);

        service.deleteExpiredReceipts();

        ArgumentCaptor<LocalDateTime> cutoff =
                ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mergeReceiptRepository, times(2)).findExpiredIds(
                cutoff.capture(),
                org.mockito.ArgumentMatchers.eq(
                        CartMergeReceiptCleanupService.DELETE_BATCH_SIZE));
        verify(mergeReceiptRepository).deleteByIds(firstBatch);
        verify(mergeReceiptRepository).deleteByIds(List.of(501L));
        verify(transactionManager, times(2))
                .commit(any(TransactionStatus.class));
        LocalDateTime after = LocalDateTime.now()
                .minusDays(CartMergeReceiptCleanupService.RETENTION_DAYS);
        assertThat(cutoff.getAllValues())
                .allSatisfy(value -> assertThat(value)
                        .isBetween(before.minusSeconds(1), after.plusSeconds(1)));
    }
}
