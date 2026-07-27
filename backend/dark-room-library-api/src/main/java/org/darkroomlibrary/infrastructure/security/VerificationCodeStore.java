package org.darkroomlibrary.infrastructure.security;

import java.util.Optional;

public interface VerificationCodeStore {

    void putCode(String purpose, String email, String code, long ttlMillis);

    boolean consumeCode(String purpose, String email, String expectedCode);

    void removeCode(String purpose, String email);

    boolean tryAcquireSendSlot(String email, String token, long ttlMillis);

    void releaseSendSlot(String email, String token);

    long incrementDailySendCount(String email, long ttlMillis);

    void clearExpired();
}
