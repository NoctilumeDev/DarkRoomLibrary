package org.darkroomlibrary.controller;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.web.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import jakarta.annotation.Resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileControllerSecurityTest extends BaseTest {

    @Resource
    private FileController fileController;

    @BeforeEach
    void setUp() {
        clearContext();
    }

    @Test
    @DisplayName("未登录直接调用上传接口仍被权限切面拒绝")
    void rejectAnonymousUploadBypass() {
        byte[] pngHeader = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        };
        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.png", "image/png", pngHeader);

        ApiResponse<String> result = fileController.uploadFile(file);

        assertEquals(400, result.getCode());
        assertEquals("身份认证失败，请先登录", result.getMsg());
    }
}
