package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.infrastructure.cache.CacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.SecureRandom;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaptchaServiceImplTest {

    @Mock
    private CacheService cacheService;

    @Mock
    private SecureRandom random;

    @InjectMocks
    private CaptchaServiceImpl captchaService;

    @BeforeEach
    void controlsOperandsWithoutChangingTheProductionGenerator() {
        ReflectionTestUtils.setField(captchaService, "random", random);
        when(random.nextInt(20)).thenReturn(6, 2);
    }

    @Test
    void verifiesAndConsumesLocalCaptchaWhenRedisIsUnavailable() {
        when(random.nextInt(3)).thenReturn(0);
        when(cacheService.setString(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(cacheService.getAndDelete(anyString())).thenReturn(Optional.empty());
        Map<String, String> captcha = captchaService.generate();
        int answer = solve(captcha.get("expression"));

        assertEquals("7 + 3 = ?", captcha.get("expression"));
        assertEquals(10, answer);

        assertTrue(captchaService.verify(captcha.get("captchaId"), answer));
        assertFalse(captchaService.verify(captcha.get("captchaId"), answer));
    }

    @Test
    void storesAndConsumesCaptchaThroughRedisWhenAvailable() {
        when(random.nextInt(3)).thenReturn(1);
        when(cacheService.setString(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        Map<String, String> captcha = captchaService.generate();
        int answer = solve(captcha.get("expression"));

        assertEquals("7 - 3 = ?", captcha.get("expression"));
        assertEquals(4, answer);
        String redisKey = "captcha:" + captcha.get("captchaId");
        when(cacheService.getAndDelete(redisKey)).thenReturn(Optional.of(String.valueOf(answer)));

        assertTrue(captchaService.verify(captcha.get("captchaId"), answer));
        verify(cacheService).setString(eq(redisKey), eq(String.valueOf(answer)), any(Duration.class));
        verify(cacheService).getAndDelete(redisKey);
    }

    @Test
    void rejectsWrongRedisAnswerAfterAtomicConsumption() {
        when(random.nextInt(3)).thenReturn(2);
        when(cacheService.setString(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        Map<String, String> captcha = captchaService.generate();
        int answer = solve(captcha.get("expression"));

        assertEquals("7 × 3 = ?", captcha.get("expression"));
        assertEquals(21, answer);
        String redisKey = "captcha:" + captcha.get("captchaId");
        when(cacheService.getAndDelete(redisKey)).thenReturn(Optional.of(String.valueOf(answer)));

        assertFalse(captchaService.verify(captcha.get("captchaId"), answer + 1));
    }

    private int solve(String expression) {
        String[] parts = expression.replace("= ?", "").trim().split("\\s+");
        int left = Integer.parseInt(parts[0]);
        int right = Integer.parseInt(parts[2]);
        return switch (parts[1]) {
            case "+" -> left + right;
            case "-" -> left - right;
            default -> left * right;
        };
    }
}
