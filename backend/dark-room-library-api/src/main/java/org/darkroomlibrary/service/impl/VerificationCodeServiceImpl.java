package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.infrastructure.security.VerificationCodeStore;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.domain.type.VerificationCodePurpose;
import org.darkroomlibrary.service.VerificationCodeService;
import org.darkroomlibrary.utils.MailUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private static final long CODE_EXPIRE_MS = 5 * 60 * 1000;
    private static final long RESEND_INTERVAL_MS = 60 * 1000;
    private static final String DEFAULT_PURPOSE = VerificationCodePurpose.REGISTER.name();

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${verification-code.daily-max-per-email:10}")
    private int dailyMaxPerEmail;

    @Resource
    private MailUtil mailUtil;

    @Resource
    private VerificationCodeStore verificationCodeStore;

    @Override
    public ApiResponse<String> sendCode(String email) {
        return sendCode(email, DEFAULT_PURPOSE);
    }

    @Override
    public ApiResponse<String> sendCode(String email, String purpose) {
        Optional<VerificationCodePurpose> normalizedPurpose = VerificationCodePurpose.from(purpose);
        if (normalizedPurpose.isEmpty()) {
            return ApiResponse.error("验证码用途不合法");
        }
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            return ApiResponse.error("请输入邮箱");
        }

        Long lastTime = verificationCodeStore.getLastSendTime(normalizedEmail).orElse(null);
        if (lastTime != null && System.currentTimeMillis() - lastTime < RESEND_INTERVAL_MS) {
            long remainSeconds = (RESEND_INTERVAL_MS - (System.currentTimeMillis() - lastTime)) / 1000;
            return ApiResponse.error("请" + remainSeconds + "秒后再试");
        }

        long sendCount = verificationCodeStore.incrementDailySendCount(normalizedEmail, millisUntilTomorrow());
        if (sendCount > dailyMaxPerEmail) {
            return ApiResponse.error("该邮箱今日验证码发送次数已达上限");
        }

        String code = String.format("%06d", secureRandom.nextInt(1000000));
        String purposeName = normalizedPurpose.get().name();
        verificationCodeStore.putCode(purposeName, normalizedEmail, code, CODE_EXPIRE_MS);
        verificationCodeStore.putLastSendTime(normalizedEmail, System.currentTimeMillis(), RESEND_INTERVAL_MS);
        try {
            mailUtil.sendVerificationCode(normalizedEmail, code);
        } catch (IllegalStateException e) {
            verificationCodeStore.removeCode(purposeName, normalizedEmail);
            log.warn("验证码邮件发送配置缺失: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            verificationCodeStore.removeCode(purposeName, normalizedEmail);
            log.error("验证码邮件发送失败: {}", normalizedEmail, e);
            return ApiResponse.error("邮件发送失败，请稍后重试");
        }

        log.info("验证码已发送到: {}", normalizedEmail);
        return ApiResponse.success("验证码已发送，请查收邮箱");
    }

    @Override
    public boolean verify(String email, String code) {
        return verify(email, DEFAULT_PURPOSE, code);
    }

    @Override
    public boolean verify(String email, String purpose, String code) {
        Optional<VerificationCodePurpose> normalizedPurpose = VerificationCodePurpose.from(purpose);
        String normalizedEmail = normalizeEmail(email);
        if (normalizedPurpose.isEmpty() || normalizedEmail == null || code == null) {
            return false;
        }

        String purposeName = normalizedPurpose.get().name();
        Optional<String> storedCode = verificationCodeStore.getCode(purposeName, normalizedEmail);
        if (storedCode.isPresent() && storedCode.get().equals(code)) {
            verificationCodeStore.removeCode(purposeName, normalizedEmail);
            return true;
        }
        return false;
    }

    @Override
    @Scheduled(fixedRate = 60000)
    public void clearExpired() {
        verificationCodeStore.clearExpired();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private long millisUntilTomorrow() {
        LocalDateTime tomorrow = LocalDate.now().plusDays(1).atStartOfDay();
        return Math.max(Duration.between(LocalDateTime.now(), tomorrow).toMillis(), 1L);
    }
}
