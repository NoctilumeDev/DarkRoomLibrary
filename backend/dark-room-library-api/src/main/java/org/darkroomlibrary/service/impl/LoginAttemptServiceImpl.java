package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.infrastructure.security.ClientIpResolver;
import org.darkroomlibrary.infrastructure.security.LoginAttemptStore;
import org.darkroomlibrary.service.LoginAttemptService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Locale;

/**
 * 登录防暴力破解服务实现（内存存储）
 */
@Service
public class LoginAttemptServiceImpl implements LoginAttemptService {

    @Value("${security.login.max-fail-attempts:5}")
    private int maxFailAttempts;

    @Value("${security.login.lock-duration-minutes:30}")
    private int lockDurationMinutes;

    @Value("${security.rate-limit.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders;

    @Resource
    private LoginAttemptStore loginAttemptStore;

    @Override
    public void loginFailed(String account) {
        loginAttemptStore.loginFailed(subject(account), maxFailAttempts, lockDurationMinutes);
    }

    @Override
    public void loginSucceeded(String account) {
        loginAttemptStore.loginSucceeded(subject(account));
    }

    @Override
    public boolean isBlocked(String account) {
        return loginAttemptStore.isBlocked(subject(account));
    }

    @Override
    public long getRemainingLockSeconds(String account) {
        return loginAttemptStore.getRemainingLockSeconds(subject(account));
    }

    @Override
    @Scheduled(fixedRate = 60000)
    public void clearExpired() {
        loginAttemptStore.clearExpired();
    }

    private String subject(String account) {
        String normalizedAccount = account == null
                ? ""
                : account.trim().toLowerCase(Locale.ROOT);
        return normalizedAccount + "|" + ClientIpResolver.resolveCurrentRequest(trustForwardedHeaders);
    }
}
