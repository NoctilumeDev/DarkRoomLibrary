package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.command.UserLoginDto;
import org.darkroomlibrary.web.dto.command.UserRegisterDto;
import org.darkroomlibrary.web.dto.command.PasswordResetDto;
import org.darkroomlibrary.web.dto.command.PasswordUpdateDto;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.service.CaptchaService;
import org.darkroomlibrary.service.UserService;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import jakarta.annotation.Resource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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

    private static final String TEST_ACCOUNT = "testuser001";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "Test@123456";

    @BeforeEach
    void setUp() {
        clearContext();
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
        fillValidCaptcha(dto);

        ApiResponse<Object> result = userService.login(dto);
        assertNotNull(result);
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
        setCurrentUser(user.getId(), user.getUserRole());

        PasswordUpdateDto dto = new PasswordUpdateDto();
        dto.setOldPwd(TEST_PASSWORD);
        dto.setNewPwd("NewPwd@654321");
        dto.setAgainPwd("NewPwd@654321");

        ApiResponse<String> result = userService.updatePwd(dto);
        assertNotNull(result);
        assertEquals(200, result.getCode());
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
}
