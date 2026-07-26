package org.darkroomlibrary.service;

import org.darkroomlibrary.web.response.ApiResponse;

/**
 * 邮箱验证码服务
 */
public interface VerificationCodeService {

    /**
     * 发送验证码到邮箱
     */
    ApiResponse<String> sendCode(String email);

    ApiResponse<String> sendCode(String email, String purpose);

    /**
     * 校验验证码
     */
    boolean verify(String email, String code);

    boolean verify(String email, String purpose, String code);

    /**
     * 清理过期验证码
     */
    void clearExpired();
}
