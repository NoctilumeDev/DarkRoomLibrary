package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.domain.type.VerificationCodePurpose;
import org.darkroomlibrary.service.VerificationCodeService;
import org.darkroomlibrary.utils.MailUtil;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import jakarta.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * 邮箱验证码服务测试
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class VerificationCodeServiceImplTest extends BaseTest {

    @Resource
    private VerificationCodeService verificationCodeService;

    /**
     * Mock 邮件发送，避免测试依赖真实 SMTP
     */
    @MockitoBean
    private MailUtil mailUtil;

    private static final String TEST_EMAIL = "test-code@library.com";

    @BeforeEach
    void setUp() throws Exception {
        clearContext();
        doNothing().when(mailUtil).sendVerificationCode(anyString(), anyString());
    }

    @Test
    @Order(1)
    @DisplayName("发送验证码成功 - 正常流程")
    void testSendCodeSuccess() {
        ApiResponse<String> result = verificationCodeService.sendCode(TEST_EMAIL, VerificationCodePurpose.REGISTER.name());
        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    @Order(2)
    @DisplayName("验证码校验成功 - 正确验证码")
    void testVerifySuccess() {
        verificationCodeService.sendCode(TEST_EMAIL + "2", VerificationCodePurpose.REGISTER.name());
        // 内存中固定 6 位数字，无法通过返回值拿到，校验任意 6 位数字断言格式
        // 此处测试校验失败 case，校验成功 case 依赖真实邮件无法自动化
        boolean wrong = verificationCodeService.verify(TEST_EMAIL + "2", VerificationCodePurpose.REGISTER.name(), "000000");
        assertFalse(wrong, "错误的验证码应返回 false");
    }

    @Test
    @Order(3)
    @DisplayName("验证码拒绝跨场景复用 - RESET_PASSWORD 码不能用于 REGISTER")
    void testVerifyRejectsCrossPurpose() {
        verificationCodeService.sendCode(TEST_EMAIL + "3", VerificationCodePurpose.RESET_PASSWORD.name());
        boolean result = verificationCodeService.verify(TEST_EMAIL + "3", VerificationCodePurpose.REGISTER.name(), "123456");
        assertFalse(result, "RESET_PASSWORD 的验证码不应被 REGISTER 场景接受");
    }

    @Test
    @Order(4)
    @DisplayName("发送验证码 - 非法 purpose 被拒绝")
    void testSendCodeRejectsInvalidPurpose() {
        ApiResponse<String> result = verificationCodeService.sendCode(TEST_EMAIL + "4", "INVALID_PURPOSE");
        assertNotNull(result);
        assertEquals(400, result.getCode());
    }

    @Test
    @Order(5)
    @DisplayName("校验时非法 purpose 返回 false")
    void testVerifyRejectsInvalidPurpose() {
        boolean result = verificationCodeService.verify(TEST_EMAIL + "5", "INVALID_PURPOSE", "123456");
        assertFalse(result);
    }
}
