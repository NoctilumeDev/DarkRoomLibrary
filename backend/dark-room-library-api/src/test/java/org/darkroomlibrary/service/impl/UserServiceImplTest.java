package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.command.UserLoginDto;
import org.darkroomlibrary.web.dto.command.UserRegisterDto;
import org.darkroomlibrary.web.dto.command.UserUpdateDto;
import org.darkroomlibrary.web.dto.command.PasswordResetDto;
import org.darkroomlibrary.web.dto.command.PasswordUpdateDto;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.domain.type.VerificationCodePurpose;
import org.darkroomlibrary.service.CaptchaService;
import org.darkroomlibrary.service.UserService;
import org.darkroomlibrary.service.VerificationCodeService;
import org.darkroomlibrary.utils.PasswordValidator;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import jakarta.annotation.Resource;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 用户服务测试
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserServiceImplTest extends BaseTest {

    @Resource
    private UserService userService;

    @Resource
    private CaptchaService captchaService;

    @MockitoBean
    private VerificationCodeService verificationCodeService;

    @Resource
    private Validator validator;

    private static final String TEST_ACCOUNT = "testuser001";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "Test@123456";

    @BeforeEach
    void setUp() {
        clearContext();
        when(verificationCodeService.verify(
                anyString(), eq(VerificationCodePurpose.REGISTER.name()), anyString()))
                .thenReturn(true);
        when(verificationCodeService.verify(
                anyString(), eq(VerificationCodePurpose.CHANGE_EMAIL.name()), anyString()))
                .thenReturn(true);
    }

    @Test
    @Order(1)
    @DisplayName("注册成功 - 正确参数")
    void testRegisterSuccess() {
        setCurrentUser(1, UserRole.ADMIN.code());
        UserRegisterDto dto = new UserRegisterDto();
        dto.setUserName("测试用户");
        dto.setUserAccount("newuser999");
        dto.setUserPwd(TEST_PASSWORD);
        dto.setUserEmail("newuser@example.test");
        // 注意：需要验证码校验，此处测试会被拦截，仅验证参数校验通过
        ApiResponse<String> result = userService.insert(dto);
        assertNotNull(result);
        assertEquals(200, result.getCode());
        // 验证码未提供，应该返回错误
        assertNotNull(result);
    }

    @Test
    @Order(2)
    @DisplayName("注册失败 - 用户名重复")
    void testRegisterDuplicateUsername() {
        setCurrentUser(1, UserRole.ADMIN.code());
        // 先创建用户
        createTestUser("dupuser001", "重复用户", "dup@example.test");

        UserRegisterDto dto = new UserRegisterDto();
        dto.setUserName("重复用户");
        dto.setUserAccount("dupuser002");
        dto.setUserPwd(TEST_PASSWORD);
        dto.setUserEmail("dup2@example.test");

        ApiResponse<String> result = userService.insert(dto);
        assertNotNull(result);
        assertEquals(400, result.getCode());
        // 用户名重复，应该在验证码校验之前或之后返回错误
    }

    @Test
    @Order(3)
    @DisplayName("注册失败 - 密码强度不足")
    void testRegisterWeakPassword() {
        setCurrentUser(1, UserRole.ADMIN.code());
        UserRegisterDto dto = new UserRegisterDto();
        dto.setUserName("弱密码用户");
        dto.setUserAccount("weakpwd001");
        dto.setUserPwd("123");
        dto.setUserEmail("weak@example.test");

        ApiResponse<String> result = userService.insert(dto);
        assertNotNull(result);
        assertEquals(400, result.getCode());
        // 密码太弱，应该被拦截
    }

    @Test
    @Order(4)
    @DisplayName("登录成功 - 正确账号密码")
    void testLoginSuccess() {
        createTestUser("loginsuccess", "登录用户", "login@example.test");

        UserLoginDto dto = new UserLoginDto();
        dto.setUserAccount("loginsuccess");
        dto.setUserPwd(TEST_PASSWORD);
        fillValidCaptcha(dto);

        ApiResponse<Object> result = userService.login(dto);
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertInstanceOf(Map.class, result.getData());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertNotNull(data.get("token"));
        assertEquals(UserRole.READER.code(), data.get("role"));
        // 登录成功应返回 token
    }

    @Test
    @Order(5)
    @DisplayName("登录失败 - 密码错误")
    void testLoginWrongPassword() {
        createTestUser("wrongpwd", "错误密码用户", "wrong@example.test");

        UserLoginDto dto = new UserLoginDto();
        dto.setUserAccount("wrongpwd");
        dto.setUserPwd("WrongPassword123");
        fillValidCaptcha(dto);

        ApiResponse<Object> result = userService.login(dto);
        assertNotNull(result);
        assertEquals(400, result.getCode());
        assertEquals("登录失败，请检查账号凭据或联系管理员", result.getMsg());
    }

    @Test
    @Order(6)
    @DisplayName("登录失败 - 账号不存在")
    void testLoginAccountNotExist() {
        UserLoginDto dto = new UserLoginDto();
        dto.setUserAccount("nonexistent999");
        dto.setUserPwd(TEST_PASSWORD);
        fillValidCaptcha(dto);

        ApiResponse<Object> result = userService.login(dto);
        assertNotNull(result);
        assertEquals(400, result.getCode());
        assertEquals("登录失败，请检查账号凭据或联系管理员", result.getMsg());
    }

    @Test
    @Order(7)
    @DisplayName("登录失败 - 账户被禁用")
    void testLoginDisabledAccount() {
        User user = createTestUser("disabled001", "禁用用户", "disabled@example.test");
        userMapper.update(User.builder().id(user.getId()).isLogin(true).build());

        UserLoginDto dto = new UserLoginDto();
        dto.setUserAccount("disabled001");
        dto.setUserPwd(TEST_PASSWORD);

        for (int attempt = 0; attempt < 5; attempt++) {
            fillValidCaptcha(dto);
            ApiResponse<Object> result = userService.login(dto);
            assertNotNull(result);
            assertEquals("登录失败，请检查账号凭据或联系管理员", result.getMsg());
        }

        fillValidCaptcha(dto);
        ApiResponse<Object> blocked = userService.login(dto);
        assertNotNull(blocked);
        assertTrue(blocked.getMsg().startsWith("登录尝试过于频繁，请"));
    }

    @Test
    @Order(8)
    @DisplayName("登录失败 - 验证码错误")
    void testLoginWrongCaptcha() {
        createTestUser("wrongcaptcha", "验证码用户", "captcha@example.test");

        UserLoginDto dto = new UserLoginDto();
        dto.setUserAccount("wrongcaptcha");
        dto.setUserPwd(TEST_PASSWORD);
        dto.setCaptchaId("invalid");
        dto.setCaptchaAnswer(0);

        ApiResponse<Object> result = userService.login(dto);
        assertNotNull(result);
        assertEquals(400, result.getCode());
        assertEquals("验证码错误或已过期", result.getMsg());
    }

    @Test
    @Order(9)
    @DisplayName("修改密码成功")
    void testUpdatePasswordSuccess() {
        User user = createTestUser("updatepwd", "改密用户", "updatepwd@example.test");
        int beforeVersion = userMapper.getById(user.getId()).getAuthVersion();
        setCurrentUser(user.getId(), user.getUserRole());

        PasswordUpdateDto dto = new PasswordUpdateDto();
        dto.setOldPwd(TEST_PASSWORD);
        dto.setNewPwd("NewPwd@654321");
        dto.setAgainPwd("NewPwd@654321");

        ApiResponse<String> result = userService.updatePwd(dto);
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(beforeVersion + 1, userMapper.getById(user.getId()).getAuthVersion());
    }

    @Test
    @Order(10)
    @DisplayName("修改密码失败 - 旧密码错误")
    void testUpdatePasswordWrongOld() {
        User user = createTestUser("wrongold", "旧密码错", "wrongold@example.test");
        setCurrentUser(user.getId(), user.getUserRole());

        PasswordUpdateDto dto = new PasswordUpdateDto();
        dto.setOldPwd("WrongOldPwd123");
        dto.setNewPwd("NewPwd@654321");
        dto.setAgainPwd("NewPwd@654321");

        ApiResponse<String> result = userService.updatePwd(dto);
        assertNotNull(result);
        assertEquals(400, result.getCode());
    }

    @Test
    @Order(11)
    @DisplayName("密码重置失败 - 账号不存在")
    void testResetPasswordAccountNotExist() {
        PasswordResetDto dto = new PasswordResetDto();
        dto.setAccount("nonexistent");
        dto.setEmail("test@example.test");
        dto.setCode("123456");
        dto.setNewPwd(TEST_PASSWORD);

        ApiResponse<String> result = userService.resetPwd(dto);
        assertNotNull(result);
        assertEquals(400, result.getCode());
    }

    @Test
    @Order(12)
    @DisplayName("查询用户失败 - 普通用户不能查看他人资料")
    void testGetByIdRejectsOtherUserForReader() {
        User currentUser = createTestUser("profile001", "资料用户1", "profile001@example.test");
        User otherUser = createTestUser("profile002", "资料用户2", "profile002@example.test");
        setCurrentUser(currentUser.getId(), currentUser.getUserRole());

        ApiResponse<?> result = userService.getById(otherUser.getId());

        assertNotNull(result);
        assertEquals(400, result.getCode());
    }

    @Test
    @Order(13)
    @DisplayName("公开注册 - 账号、署名、密码和共享邮箱组合遵守确定规则")
    void testRegistrationIdentityCombinationsAndSharedEmailLimit() {
        UserRegisterDto invalidBoundary = registration(
                "combo_invalid", "组合边界", TEST_PASSWORD, "x".repeat(90) + "@example.test");
        invalidBoundary.setVerificationCode("12345");
        var boundaryMessages = validator.validate(invalidBoundary).stream()
                .map(violation -> violation.getMessage())
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(boundaryMessages.contains("邮箱不能超过100个字符"));
        assertTrue(boundaryMessages.contains("邮箱验证码为6位数字"));

        String sharedEmail = "Shared.Registration@example.test";
        ApiResponse<String> first = userService.register(registration(
                "combo_account_1", "组合署名甲", TEST_PASSWORD, sharedEmail));
        ApiResponse<String> second = userService.register(registration(
                "combo_account_2", "组合署名乙", TEST_PASSWORD, sharedEmail.toLowerCase()));
        ApiResponse<String> third = userService.register(registration(
                "combo_account_3", "组合署名丙", "Different@12345", sharedEmail));

        assertEquals(200, first.getCode());
        assertEquals(200, second.getCode());
        assertEquals(200, third.getCode());
        assertEquals(3, userMapper.countByNormalizedEmail(sharedEmail.toLowerCase()));

        when(verificationCodeService.verify(
                anyString(), eq(VerificationCodePurpose.REGISTER.name()), anyString()))
                .thenReturn(false);
        ApiResponse<String> unverified = userService.register(registration(
                "combo_account_hidden", "组合署名隐藏", TEST_PASSWORD, sharedEmail));
        assertEquals(400, unverified.getCode());
        assertEquals("验证码错误或已过期", unverified.getMsg());
        when(verificationCodeService.verify(
                anyString(), eq(VerificationCodePurpose.REGISTER.name()), anyString()))
                .thenReturn(true);

        ApiResponse<String> fourth = userService.register(registration(
                "combo_account_4", "组合署名丁", TEST_PASSWORD, sharedEmail));
        assertEquals(400, fourth.getCode());
        assertEquals(UserEmailQuotaService.LIMIT_MESSAGE, fourth.getMsg());

        ApiResponse<String> duplicateAccountDifferentPassword = userService.register(registration(
                "combo_account_1", "组合署名戊", "Another@12345", "other-1@example.test"));
        ApiResponse<String> duplicateAccountSamePassword = userService.register(registration(
                "combo_account_1", "组合署名己", TEST_PASSWORD, "other-2@example.test"));
        ApiResponse<String> duplicateDisplayName = userService.register(registration(
                "combo_account_5", "组合署名甲", TEST_PASSWORD, "other-3@example.test"));

        assertEquals("账号不可用", duplicateAccountDifferentPassword.getMsg());
        assertEquals("账号不可用", duplicateAccountSamePassword.getMsg());
        assertEquals("用户名已经被使用，请换一个", duplicateDisplayName.getMsg());

        User firstUser = userMapper.getByActive(User.builder().userAccount("combo_account_1").build());
        User secondUser = userMapper.getByActive(User.builder().userAccount("combo_account_2").build());
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        assertTrue(passwordEncoder.matches(TEST_PASSWORD, firstUser.getUserPwd()));
        assertTrue(passwordEncoder.matches(TEST_PASSWORD, secondUser.getUserPwd()));
        assertNotEquals(firstUser.getUserPwd(), secondUser.getUserPwd());
        assertEquals(sharedEmail.toLowerCase(), firstUser.getUserEmail());
    }

    @Test
    @Order(14)
    @DisplayName("公开注册 - 并发共享同一邮箱最多三个账号成功")
    void testConcurrentRegistrationsRespectSharedEmailLimit() throws Exception {
        int attempts = 12;
        String sharedEmail = "concurrent-shared@example.test";
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ApiResponse<String>>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < attempts; index++) {
                int attempt = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return userService.register(registration(
                            "quota_concurrent_" + attempt,
                            "并发共享署名" + attempt,
                            TEST_PASSWORD,
                            sharedEmail));
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<ApiResponse<String>> results = new ArrayList<>();
            for (Future<ApiResponse<String>> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            long successes = results.stream().filter(result -> result.getCode() == 200).count();
            assertEquals(3, successes);
            assertTrue(results.stream()
                    .filter(result -> result.getCode() != 200)
                    .allMatch(result -> UserEmailQuotaService.LIMIT_MESSAGE.equals(result.getMsg())));
            assertEquals(3, userMapper.countByNormalizedEmail(sharedEmail));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    @Order(15)
    @DisplayName("邮箱配额 - 改邮箱受限且物理删除后释放名额")
    void testEmailChangeAndPhysicalDeleteMaintainQuota() {
        setCurrentUser(999_999, UserRole.ADMIN.code());
        String sharedEmail = "update-shared@example.test";
        for (int index = 0; index < 3; index++) {
            ApiResponse<String> created = userService.insert(registration(
                    "quota_update_" + index,
                    "配额更新署名" + index,
                    TEST_PASSWORD,
                    sharedEmail));
            assertEquals(200, created.getCode());
        }
        ApiResponse<String> targetCreated = userService.insert(registration(
                "quota_update_target",
                "配额更新目标",
                TEST_PASSWORD,
                "target-before@example.test"));
        assertEquals(200, targetCreated.getCode());

        User target = userMapper.getByActive(
                User.builder().userAccount("quota_update_target").build());
        setCurrentUser(target.getId(), UserRole.READER.code());
        UserUpdateDto update = new UserUpdateDto();
        update.setUserEmail(sharedEmail);
        ApiResponse<String> missingCode = userService.update(update);
        assertEquals(400, missingCode.getCode());
        assertEquals("请输入新邮箱验证码", missingCode.getMsg());

        when(verificationCodeService.verify(
                eq(sharedEmail), eq(VerificationCodePurpose.CHANGE_EMAIL.name()), eq("000000")))
                .thenReturn(false);
        update.setVerificationCode("000000");
        ApiResponse<String> unverified = userService.update(update);
        assertEquals(400, unverified.getCode());
        assertEquals("验证码错误或已过期", unverified.getMsg());

        update.setVerificationCode("123456");
        ApiResponse<String> rejected = userService.update(update);
        assertEquals(400, rejected.getCode());
        assertEquals(UserEmailQuotaService.LIMIT_MESSAGE, rejected.getMsg());

        User released = userMapper.getByActive(
                User.builder().userAccount("quota_update_0").build());
        setCurrentUser(999_999, UserRole.ADMIN.code());
        assertEquals(200, userService.batchDelete(List.of(released.getId())).getCode());

        setCurrentUser(target.getId(), UserRole.READER.code());
        ApiResponse<String> accepted = userService.update(update);
        assertEquals(200, accepted.getCode());
        assertEquals(3, userMapper.countByNormalizedEmail(sharedEmail));
    }

    @Test
    @Order(16)
    @DisplayName("公开注册与后台新增 - 新密码上限在 HTTP 与服务边界一致")
    void testNewPasswordUpperBoundaryAtValidationAndServiceLayers() {
        UserRegisterDto accepted = registration(
                "password_limit_ok", "密码上限通过", "Aa1!" + "x".repeat(16), "limit-ok@example.test");
        assertTrue(validator.validate(accepted).isEmpty());

        UserRegisterDto rejected = registration(
                "password_limit_bad", "密码上限拒绝", "Aa1!" + "x".repeat(17), "limit-bad@example.test");
        var messages = validator.validate(rejected).stream()
                .map(violation -> violation.getMessage())
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(messages.contains("密码长度需要在8-20位之间"));

        assertEquals(PasswordValidator.getRequirement(), userService.register(rejected).getMsg());
        setCurrentUser(999_999, UserRole.ADMIN.code());
        assertEquals(PasswordValidator.getRequirement(), userService.insert(rejected).getMsg());
    }

    private void fillValidCaptcha(UserLoginDto dto) {
        Map<String, String> captcha = captchaService.generate();
        dto.setCaptchaId(captcha.get("captchaId"));
        dto.setCaptchaAnswer(resolveCaptchaAnswer(captcha.get("expression")));
    }

    private int resolveCaptchaAnswer(String expression) {
        String[] parts = expression.replace("= ?", "").trim().split(" ");
        int left = Integer.parseInt(parts[0]);
        int right = Integer.parseInt(parts[2]);
        switch (parts[1]) {
            case "+":
                return left + right;
            case "-":
                return left - right;
            case "×":
                return left * right;
            default:
                throw new IllegalArgumentException("Unsupported captcha expression: " + expression);
        }
    }

    private UserRegisterDto registration(String account,
                                         String name,
                                         String password,
                                         String email) {
        UserRegisterDto dto = new UserRegisterDto();
        dto.setUserAccount(account);
        dto.setUserName(name);
        dto.setUserPwd(password);
        dto.setUserEmail(email);
        dto.setVerificationCode("123456");
        return dto;
    }
}
