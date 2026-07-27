package org.darkroomlibrary.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionCallbacksTest {

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void runsImmediatelyWhenSynchronizationHasNoActualTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicBoolean executed = new AtomicBoolean(false);

        TransactionCallbacks.afterCommit(() -> executed.set(true));

        assertTrue(executed.get());
    }

    @Test
    void defersUntilCommitWhenActualTransactionIsActive() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        AtomicBoolean executed = new AtomicBoolean(false);

        TransactionCallbacks.afterCommit(() -> executed.set(true));

        assertFalse(executed.get());
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        assertTrue(executed.get());
    }

    @Test
    void postCommitFailureDoesNotEscapeAfterBusinessCommit() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        TransactionCallbacks.afterCommit(() -> {
            throw new IllegalStateException("side effect failed");
        });

        assertDoesNotThrow(() -> {
            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
        });
    }
}
