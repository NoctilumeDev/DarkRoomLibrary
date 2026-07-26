package org.darkroomlibrary.controller;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.mapper.StoredFileMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.domain.model.StoredFile;
import org.darkroomlibrary.domain.type.FileReferenceType;
import org.darkroomlibrary.domain.type.StoredFileStatus;
import org.darkroomlibrary.service.impl.FileStorageServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileControllerTest {

    @TempDir
    private Path tempDir;

    private FileController fileController;
    private StoredFileMapper storedFileMapper;

    @BeforeEach
    void setUp() {
        storedFileMapper = mock(StoredFileMapper.class);
        FileStorageServiceImpl fileStorageService = new FileStorageServiceImpl();
        ReflectionTestUtils.setField(fileStorageService, "apiPrefix", "/api/test");
        ReflectionTestUtils.setField(fileStorageService, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(fileStorageService, "temporaryRetentionHours", 24L);
        ReflectionTestUtils.setField(fileStorageService, "storedFileMapper", storedFileMapper);
        fileController = new FileController();
        ReflectionTestUtils.setField(fileController, "fileStorageService", fileStorageService);
        CurrentUserContext.bind(7, 2);
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    @DisplayName("图片上传成功 - 使用随机文件名并写入安全目录")
    void uploadValidPng() {
        byte[] pngHeader = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D
        };
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cover.png",
                "image/png",
                pngHeader
        );

        ApiResponse<String> result = fileController.uploadFile(file);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertTrue(result.getData().startsWith("/api/test/file/public?fileName="));

        String fileName = result.getData().substring(result.getData().indexOf("fileName=") + "fileName=".length());
        assertTrue(fileName.matches("^[a-fA-F0-9]{32}\\.png$"));
        assertTrue(Files.exists(tempDir.resolve(fileName)));
        verify(storedFileMapper).insert(any(StoredFile.class));
    }

    @Test
    @DisplayName("上传失败 - MIME 与扩展名不匹配")
    void rejectMismatchedMimeType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bad.jpg",
                "text/html",
                "<html><body>x</body></html>".getBytes(StandardCharsets.UTF_8)
        );

        ApiResponse<String> result = fileController.uploadFile(file);

        assertNotNull(result);
        assertEquals(400, result.getCode());
    }

    @Test
    @DisplayName("上传失败 - 不支持的文件类型")
    void rejectUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "tool.exe",
                "application/octet-stream",
                new byte[]{0x4D, 0x5A}
        );

        ApiResponse<String> result = fileController.uploadFile(file);

        assertNotNull(result);
        assertEquals(400, result.getCode());
    }

    @Test
    @DisplayName("上传失败 - 正确 MIME 不能掩盖错误文件签名")
    void rejectSpoofedPngSignature() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.png",
                "image/png",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)
        );

        ApiResponse<String> result = fileController.uploadFile(file);

        assertEquals(400, result.getCode());
        try (java.util.stream.Stream<Path> files = Files.list(tempDir)) {
            assertTrue(files.findAny().isEmpty());
        }
    }

    @Test
    @DisplayName("上传失败 - 主动内容 SVG 不在白名单")
    void rejectSvgUpload() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "active.svg",
                "image/svg+xml",
                "<svg onload=\"alert(1)\"></svg>".getBytes(StandardCharsets.UTF_8)
        );

        ApiResponse<String> result = fileController.uploadFile(file);

        assertEquals(400, result.getCode());
    }

    @Test
    @DisplayName("上传失败 - 双扩展名不能伪装成图片")
    void rejectDoubleExtensionDisguisedAsImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "active.html.png",
                "image/png",
                "<!doctype html><html><body>x</body></html>".getBytes(StandardCharsets.UTF_8)
        );

        ApiResponse<String> result = fileController.uploadFile(file);

        assertEquals(400, result.getCode());
    }

    @Test
    @DisplayName("上传失败 - 缺失 MIME 类型不能绕过校验")
    void rejectMissingMimeType() {
        byte[] pngHeader = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        };
        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.png", null, pngHeader);

        ApiResponse<String> result = fileController.uploadFile(file);

        assertEquals(400, result.getCode());
    }

    @Test
    @DisplayName("上传失败 - 视频上传别名不能绕过文件白名单")
    void rejectIllegalTypeThroughVideoEndpoint() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "script.js",
                "application/javascript",
                "alert(1)".getBytes(StandardCharsets.UTF_8)
        );

        ApiResponse<String> result = fileController.videoUpload(file);

        assertEquals(400, result.getCode());
    }

    @Test
    @DisplayName("下载 HTML - 强制附件下载并禁用嗅探")
    void forceDownloadHtmlFile() throws Exception {
        String fileName = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.html";
        byte[] html = "<!doctype html><html><body>x</body></html>"
                .getBytes(StandardCharsets.UTF_8);
        Files.write(tempDir.resolve(fileName), html);
        when(storedFileMapper.selectById(fileName)).thenReturn(StoredFile.builder()
                .fileName(fileName)
                .originalName("message.html")
                .status(StoredFileStatus.BOUND.getStatus())
                .refType(FileReferenceType.MESSAGE_ATTACHMENT.getValue())
                .refId(1)
                .build());

        MockHttpServletResponse response = new MockHttpServletResponse();
        fileController.download(fileName, response);

        assertEquals(200, response.getStatus());
        assertEquals("application/octet-stream", response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").contains("attachment"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertArrayEquals(html, response.getContentAsByteArray());
    }

    @Test
    @DisplayName("下载失败 - 拒绝路径穿越")
    void rejectPathTraversalDownload() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        fileController.getFile("../aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.html", response);

        assertEquals(400, response.getStatus());
    }

    @Test
    @DisplayName("下载失败 - 真实存在的非白名单类型仍不可访问")
    void rejectExistingUnsupportedFileDownload() throws Exception {
        String fileName = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.txt";
        Files.writeString(tempDir.resolve(fileName), "private", StandardCharsets.UTF_8);
        MockHttpServletResponse response = new MockHttpServletResponse();

        fileController.getFile(fileName, response);

        assertEquals(400, response.getStatus());
        assertEquals(0, response.getContentAsByteArray().length);
    }
}
