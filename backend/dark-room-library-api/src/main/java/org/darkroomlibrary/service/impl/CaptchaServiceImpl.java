package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.service.CaptchaService;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptchaServiceImpl implements CaptchaService {

    private static final long CAPTCHA_EXPIRE_MS = 5 * 60 * 1000;

    private final Map<String, CaptchaEntry> captchaMap = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Override
    public Map<String, String> generate() {
        int a = random.nextInt(20) + 1;
        int b = random.nextInt(20) + 1;
        int op = random.nextInt(3);
        int answer;
        String expression;
        switch (op) {
            case 0:
                answer = a + b;
                expression = a + " + " + b + " = ?";
                break;
            case 1:
                answer = a - b;
                expression = a + " - " + b + " = ?";
                break;
            default:
                answer = a * b;
                expression = a + " × " + b + " = ?";
                break;
        }

        String captchaId = UUID.randomUUID().toString().replace("-", "");
        captchaMap.put(captchaId, new CaptchaEntry(answer, System.currentTimeMillis() + CAPTCHA_EXPIRE_MS));

        Map<String, String> result = new HashMap<>();
        result.put("captchaId", captchaId);
        result.put("expression", expression);
        return result;
    }

    @Override
    public boolean verify(String captchaId, Integer answer) {
        if (captchaId == null || captchaId.trim().isEmpty() || answer == null) {
            return false;
        }
        CaptchaEntry entry = captchaMap.remove(captchaId);
        if (entry == null || System.currentTimeMillis() > entry.expireTime) {
            return false;
        }
        return entry.answer == answer;
    }

    @Scheduled(fixedRate = 60000)
    public void clearExpired() {
        long now = System.currentTimeMillis();
        captchaMap.entrySet().removeIf(entry -> now > entry.getValue().expireTime);
    }

    private static class CaptchaEntry {
        private final int answer;
        private final long expireTime;

        private CaptchaEntry(int answer, long expireTime) {
            this.answer = answer;
            this.expireTime = expireTime;
        }
    }
}
