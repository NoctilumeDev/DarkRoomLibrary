package org.darkroomlibrary.controller;

import org.darkroomlibrary.exception.GlobalExceptionHandler;
import org.darkroomlibrary.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerValidationTest {

    private MockMvc mockMvc;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void rejectsOversizedNewPasswordBeforeRegisterOrAdminInsertServiceCall() throws Exception {
        String requestBody = """
                {
                  "userName": "密码边界用户",
                  "userAccount": "password_boundary",
                  "userPwd": "Aa1!xxxxxxxxxxxxxxxxx",
                  "userEmail": "password-boundary@example.test",
                  "verificationCode": "123456"
                }
                """;

        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value(containsString("密码长度需要在8-20位之间")));

        mockMvc.perform(post("/user/insert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value(containsString("密码长度需要在8-20位之间")));

        verifyNoInteractions(userService);
    }
}
