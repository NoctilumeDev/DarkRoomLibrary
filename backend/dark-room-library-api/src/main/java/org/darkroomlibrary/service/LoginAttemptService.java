package org.darkroomlibrary.service;

/**
 * 登录防暴力破解服务
 */
public interface LoginAttemptService {

    /**
     * 记录登录失败
     */
    void loginFailed(String account);

    /**
     * 登录成功，清除计数
     */
    void loginSucceeded(String account);

    /**
     * 检查是否被锁定
     */
    boolean isBlocked(String account);

    /**
     * 获取剩余锁定时间（秒），未锁定返回0
     */
    long getRemainingLockSeconds(String account);

    /**
     * 清理过期记录
     */
    void clearExpired();
}