package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.infrastructure.security.LoginAttemptStore;
import org.darkroomlibrary.service.LoginAttemptService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * 登录防暴力破解服务实现（内存存储）
 */
@Service
public class LoginAttemptServiceImpl implements LoginAttemptService {

    @Value("${security.login.max-fail-attempts:5}")
    private int maxFailAttempts;

    @Value("${security.login.lock-duration-minutes:30}")
    private int lockDurationMinutes;

    @Resource
    private LoginAttemptStore loginAttemptStore;

    @Override
    public void loginFailed(String account) {
        loginAttemptStore.loginFailed(account, maxFailAttempts, lockDurationMinutes);
    }

    @Override
    public void loginSucceeded(String account) {
        loginAttemptStore.loginSucceeded(account);
    }

    @Override
    public boolean isBlocked(String account) {
        return loginAttemptStore.isBlocked(account);
    }

    @Override
    public long getRemainingLockSeconds(String account) {
        return loginAttemptStore.getRemainingLockSeconds(account);
    }

    @Override
    @Scheduled(fixedRate = 60000)
    public void clearExpired() {
        loginAttemptStore.clearExpired();
    }
}
