package org.darkroomlibrary.infrastructure.security;

public interface LoginAttemptStore {

    void loginFailed(String account, int maxFailAttempts, int lockDurationMinutes);

    void loginSucceeded(String account);

    boolean isBlocked(String account);

    long getRemainingLockSeconds(String account);

    void clearExpired();
}
