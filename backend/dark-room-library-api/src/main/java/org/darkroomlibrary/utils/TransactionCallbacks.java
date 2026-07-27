package org.darkroomlibrary.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Keeps non-transactional side effects outside the business commit boundary.
 */
public final class TransactionCallbacks {

    private static final Logger log = LoggerFactory.getLogger(TransactionCallbacks.class);

    private TransactionCallbacks() {
    }

    public static void afterCommit(Runnable task) {
        Runnable safeTask = () -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.warn("提交后任务执行失败，业务事务已提交: {}", e.getMessage(), e);
            }
        };
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            safeTask.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeTask.run();
            }
        });
    }
}
