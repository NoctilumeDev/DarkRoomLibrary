package org.darkroomlibrary.infrastructure.security;

import java.util.Optional;

public interface VerificationCodeStore {

    void putCode(String purpose, String email, String code, long ttlMillis);

    Optional<String> getCode(String purpose, String email);

    void removeCode(String purpose, String email);

    Optional<Long> getLastSendTime(String email);

    void putLastSendTime(String email, long timestamp, long ttlMillis);

    long incrementDailySendCount(String email, long ttlMillis);

    void clearExpired();
}
