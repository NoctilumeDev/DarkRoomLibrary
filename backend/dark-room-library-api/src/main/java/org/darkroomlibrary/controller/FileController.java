package org.darkroomlibrary.controller;

import org.darkroomlibrary.aop.ManualAudit;
import org.darkroomlibrary.aop.NormalizePageQuery;
import org.darkroomlibrary.aop.RequireRole;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.StoredFilePageQuery;
import org.darkroomlibrary.web.view.StoredFileView;
import org.darkroomlibrary.service.FileStorageService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/file")
public class FileController {

    @Resource
    private FileStorageService fileStorageService;

    @RequireRole
    @PostMapping("/upload")
    public ApiResponse<String> uploadFile(@RequestParam("file") MultipartFile multipartFile) {
        return fileStorageService.upload(multipartFile);
    }

    @RequireRole
    @PostMapping("/video/upload")
    public ApiResponse<String> videoUpload(@RequestParam("file") MultipartFile multipartFile) {
        return fileStorageService.upload(multipartFile);
    }

    /**
     * 兼容旧链接；受文件元数据的公开范围约束。
     */
    @GetMapping("/getFile")
    public void getFile(@RequestParam("fileName") String fileName,
                        HttpServletResponse response) throws IOException {
        fileStorageService.writePublicFile(fileName, response);
    }

    @GetMapping("/public")
    public void publicFile(@RequestParam("fileName") String fileName,
                           HttpServletResponse response) throws IOException {
        fileStorageService.writePublicFile(fileName, response);
    }

    @RequireRole
    @GetMapping("/download")
    public void download(@RequestParam("fileName") String fileName,
                         HttpServletResponse response) throws IOException {
        fileStorageService.writeDownload(fileName, response);
    }

    @NormalizePageQuery
    @RequireRole(UserRole.SUPER_ADMIN)
    @PostMapping("/query")
    public ApiResponse<List<StoredFileView>> query(@RequestBody StoredFilePageQuery dto) {
        return fileStorageService.query(dto);
    }

    @RequireRole(UserRole.SUPER_ADMIN)
    @ManualAudit
    @DeleteMapping("/unbound")
    public ApiResponse<Void> deleteUnbound(@RequestParam("fileName") String fileName) {
        return fileStorageService.deleteUnbound(fileName);
    }

    @RequireRole(UserRole.SUPER_ADMIN)
    @ManualAudit
    @PostMapping("/cleanup")
    public ApiResponse<Map<String, Object>> cleanup() {
        return fileStorageService.cleanupNow();
    }
}
