package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.mapper.StoredFileMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.response.PageResponse;
import org.darkroomlibrary.web.dto.query.StoredFilePageQuery;
import org.darkroomlibrary.domain.type.FileReferenceType;
import org.darkroomlibrary.domain.type.StoredFileStatus;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.domain.model.StoredFile;
import org.darkroomlibrary.web.view.StoredFileView;
import org.darkroomlibrary.service.FileStorageService;
import org.darkroomlibrary.utils.FileIdGenerator;
import org.darkroomlibrary.utils.TransactionCallbacks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final long MB = 1024L * 1024L;
    private static final int CLEANUP_BATCH_SIZE = 200;
    private static final int DELETION_LEASE_MINUTES = 10;
    private static final Pattern SAFE_FILE_NAME =
            Pattern.compile("^[a-fA-F0-9]{32}\\.[a-z0-9]{2,5}$");
    private static final Pattern FILE_NAME_IN_URL =
            Pattern.compile("(?:[?&]fileName=)([a-fA-F0-9]{32}\\.[a-z0-9]{2,5})(?:[&#\\\"']|$)");
    private static final Set<String> OCTET_STREAM_TYPES = Set.of("", "application/octet-stream");
    private static final Map<String, FileRule> FILE_RULES = new HashMap<>();

    static {
        putRule(".jpg", 5 * MB, true, "image/jpeg");
        putRule(".jpeg", 5 * MB, true, "image/jpeg");
        putRule(".png", 5 * MB, true, "image/png");
        putRule(".gif", 5 * MB, true, "image/gif");
        putRule(".bmp", 5 * MB, true, "image/bmp", "image/x-ms-bmp");
        putRule(".webp", 5 * MB, true, "image/webp");
        putRule(".mp4", 10 * MB, true, "video/mp4", "application/mp4");
        putRule(".avi", 10 * MB, true, "video/x-msvideo", "video/avi", "video/msvideo");
        putRule(".mov", 10 * MB, true, "video/quicktime");
        putRule(".wmv", 10 * MB, true, "video/x-ms-wmv");
        putRule(".pdf", 10 * MB, false, "application/pdf");
        putRule(".doc", 10 * MB, false, "application/msword", "application/vnd.ms-word");
        putRule(".docx", 10 * MB, false,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        putRule(".xls", 10 * MB, false, "application/vnd.ms-excel");
        putRule(".xlsx", 10 * MB, false,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        putRule(".ppt", 10 * MB, false, "application/vnd.ms-powerpoint");
        putRule(".pptx", 10 * MB, false,
                "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        putRule(".html", 1 * MB, false, "text/html", "application/xhtml+xml");
        putRule(".htm", 1 * MB, false, "text/html", "application/xhtml+xml");
    }

    @Value("${app.api-prefix}")
    private String apiPrefix;

    @Value("${file.upload-dir:./upload/pic}")
    private String uploadDir;

    @Value("${file.temp-retention-hours:24}")
    private long temporaryRetentionHours;

    @Resource
    private StoredFileMapper storedFileMapper;

    @Resource
    private PlatformTransactionManager transactionManager;

    @Override
    public ApiResponse<String> upload(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            return ApiResponse.error("文件不能为空");
        }
        String originalFilename = safeOriginalFilename(multipartFile.getOriginalFilename());
        if (originalFilename == null || !originalFilename.contains(".")) {
            return ApiResponse.error("文件类型不支持");
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        FileRule rule = FILE_RULES.get(extension);
        if (rule == null) {
            return ApiResponse.error("不支持的文件类型: " + extension);
        }
        if (multipartFile.getSize() > rule.maxSize) {
            return ApiResponse.error("文件大小不能超过 " + (rule.maxSize / MB) + "MB");
        }
        if (!rule.allowsContentType(multipartFile.getContentType())) {
            return ApiResponse.error("文件 MIME 类型与扩展名不匹配");
        }
        try {
            if (!hasExpectedFileSignature(multipartFile, extension)) {
                return ApiResponse.error("文件内容与扩展名不匹配");
            }
        } catch (IOException e) {
            return ApiResponse.error("文件读取异常");
        }

        String fileName = FileIdGenerator.nextId() + extension;
        Path target = resolveTarget(fileName);
        if (target == null) {
            return ApiResponse.error("文件名不合法");
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            Files.createDirectories(target.getParent());
            multipartFile.transferTo(target.toFile());
            StoredFile storedFile = StoredFile.builder()
                    .fileName(fileName)
                    .originalName(originalFilename)
                    .extension(extension.substring(1))
                    .contentType(normalizeContentType(multipartFile.getContentType()))
                    .fileSize(multipartFile.getSize())
                    .uploaderId(CurrentUserContext.userId())
                    .status(StoredFileStatus.TEMPORARY.getStatus())
                    .createTime(now)
                    .updateTime(now)
                    .build();
            if (storedFileMapper.insert(storedFile) != 1) {
                throw new IllegalStateException("文件元数据写入失败");
            }
            String endpoint = rule.publicPreview ? "/file/public" : "/file/download";
            return ApiResponse.success("上传成功", apiPrefix + endpoint + "?fileName=" + fileName);
        } catch (Exception e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanupError) {
                log.warn("上传失败后的磁盘清理失败: fileName={}", fileName, cleanupError);
            }
            log.warn("文件上传失败: originalName={}, error={}", originalFilename, e.getMessage());
            return ApiResponse.error("文件上传异常");
        }
    }

    @Override
    public boolean bindSingle(String fileUrl, FileReferenceType refType, Integer refId) {
        if (refType == null || refId == null) {
            return false;
        }
        String fileName = extractFileName(fileUrl);
        if (fileName == null) {
            releaseReference(refType, refId);
            return fileUrl == null || fileUrl.isBlank();
        }
        StoredFile target = storedFileMapper.selectById(fileName);
        if (target == null) {
            log.info("业务引用使用旧文件，跳过元数据绑定: fileName={}, refType={}, refId={}",
                    fileName, refType.getValue(), refId);
            return true;
        }
        if (!canBind(target, refType, refId)) {
            return false;
        }
        List<StoredFile> existing = storedFileMapper.findByReference(refType.getValue(), refId);
        List<StoredFile> stale = existing.stream()
                .filter(file -> !fileName.equals(file.getFileName()))
                .collect(Collectors.toList());
        markDeletePending(stale);
        return bindManagedFile(fileName, refType, refId);
    }

    @Override
    public boolean bindFromHtml(String html, FileReferenceType refType, Integer refId) {
        if (refType == null || refId == null) {
            return false;
        }
        Set<String> desired = extractFileNames(html);
        for (String fileName : desired) {
            StoredFile file = storedFileMapper.selectById(fileName);
            if (file != null && !canBind(file, refType, refId)) {
                return false;
            }
        }

        List<StoredFile> existing = storedFileMapper.findByReference(refType.getValue(), refId);
        List<StoredFile> stale = existing.stream()
                .filter(file -> !desired.contains(file.getFileName()))
                .collect(Collectors.toList());
        markDeletePending(stale);
        for (String fileName : desired) {
            if (storedFileMapper.selectById(fileName) != null
                    && !bindManagedFile(fileName, refType, refId)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void releaseReference(FileReferenceType refType, Integer refId) {
        if (refType == null || refId == null) {
            return;
        }
        markDeletePending(storedFileMapper.findByReference(refType.getValue(), refId));
    }

    @Override
    public void releaseReferences(FileReferenceType refType, List<Integer> refIds) {
        if (refIds == null) {
            return;
        }
        for (Integer refId : refIds) {
            releaseReference(refType, refId);
        }
    }

    @Override
    public void releaseUserBusinessFiles(List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        markDeletePending(storedFileMapper.findUserBusinessFiles(userIds));
    }

    @Override
    public String toDownloadUrl(String fileUrl) {
        String fileName = extractFileName(fileUrl);
        return fileName == null ? fileUrl : apiPrefix + "/file/download?fileName=" + fileName;
    }

    @Override
    public void writePublicFile(String fileName, HttpServletResponse response) throws IOException {
        FileRule rule = validateRequestFile(fileName, response);
        if (rule == null) {
            return;
        }
        StoredFile storedFile = storedFileMapper.selectById(fileName);
        if (!isPubliclyAccessible(storedFile, rule)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        write(fileName, storedFile, rule.responseContentType, false, response);
    }

    @Override
    public void writeDownload(String fileName, HttpServletResponse response) throws IOException {
        FileRule rule = validateRequestFile(fileName, response);
        if (rule == null) {
            return;
        }
        StoredFile storedFile = storedFileMapper.selectById(fileName);
        if (!canDownload(storedFile)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        write(fileName, storedFile, "application/octet-stream", true, response);
    }

    @Override
    public ApiResponse<List<StoredFileView>> query(StoredFilePageQuery dto) {
        List<StoredFileView> files = storedFileMapper.query(dto);
        for (StoredFileView file : files) {
            Path target = resolveTarget(file.getFileName());
            file.setDiskExists(target != null && Files.isRegularFile(target));
            FileReferenceType refType = FileReferenceType.fromValue(file.getRefType());
            FileRule rule = FILE_RULES.get("." + file.getExtension().toLowerCase());
            boolean publicPreview = rule != null && rule.publicPreview
                    && refType != FileReferenceType.MESSAGE_ATTACHMENT;
            String endpoint = publicPreview ? "/file/public" : "/file/download";
            file.setAccessUrl(apiPrefix + endpoint + "?fileName=" + file.getFileName());
        }
        return PageResponse.success(files, storedFileMapper.queryCount(dto));
    }

    @Override
    public ApiResponse<Void> deleteUnbound(String fileName) {
        if (!isSafeFileName(fileName)) {
            return ApiResponse.error("文件名不合法");
        }
        StoredFile storedFile = storedFileMapper.selectById(fileName);
        if (storedFile == null) {
            return ApiResponse.error("文件记录不存在");
        }
        if (Objects.equals(storedFile.getStatus(), StoredFileStatus.BOUND.getStatus())) {
            return ApiResponse.error("文件仍被业务引用，不能直接删除");
        }
        StoredFile claimed = claimUnboundForDeletion(fileName, LocalDateTime.now());
        if (claimed == null) {
            return ApiResponse.error("文件状态已变化，请刷新后重试");
        }
        return deleteStoredFile(claimed)
                ? ApiResponse.success("文件已删除")
                : ApiResponse.error("文件删除失败，已保留待重试记录");
    }

    @Override
    public ApiResponse<Map<String, Object>> cleanupNow() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusHours(temporaryRetentionHours);
        LocalDateTime deletingLeaseCutoff = now.minusMinutes(DELETION_LEASE_MINUTES);
        List<StoredFile> candidates = storedFileMapper.findCleanupCandidates(
                cutoff, deletingLeaseCutoff, CLEANUP_BATCH_SIZE);
        int metadataDeleted = 0;
        for (StoredFile candidate : candidates) {
            StoredFile claimed = claimForCleanup(
                    candidate.getFileName(), cutoff, deletingLeaseCutoff, LocalDateTime.now());
            if (claimed != null && deleteStoredFile(claimed)) {
                metadataDeleted++;
            }
        }

        int diskOrphansDeleted = 0;
        Path root = uploadRoot();
        if (Files.isDirectory(root)) {
            try (Stream<Path> paths = Files.list(root)) {
                List<Path> orphanCandidates = paths
                        .filter(Files::isRegularFile)
                        .filter(path -> isSafeFileName(path.getFileName().toString()))
                        .limit(CLEANUP_BATCH_SIZE)
                        .collect(Collectors.toList());
                for (Path path : orphanCandidates) {
                    String fileName = path.getFileName().toString();
                    if (storedFileMapper.selectById(fileName) != null || !isOlderThan(path, cutoff)) {
                        continue;
                    }
                    Integer references = storedFileMapper.countLegacyReferences(fileName);
                    if (references != null && references > 0) {
                        continue;
                    }
                    if (Files.deleteIfExists(path)) {
                        diskOrphansDeleted++;
                    }
                }
            } catch (IOException e) {
                log.warn("扫描孤儿文件失败: {}", e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("metadataDeleted", metadataDeleted);
        result.put("diskOrphansDeleted", diskOrphansDeleted);
        result.put("temporaryRetentionHours", temporaryRetentionHours);
        result.put("deletionLeaseMinutes", DELETION_LEASE_MINUTES);
        return ApiResponse.success(result);
    }

    @Scheduled(cron = "${file.cleanup-cron:0 30 3 * * ?}")
    public void scheduledCleanup() {
        ApiResponse<Map<String, Object>> result = cleanupNow();
        log.info("文件清理完成: {}", result.getData());
    }

    private boolean bindManagedFile(String fileName, FileReferenceType refType, Integer refId) {
        int updated = storedFileMapper.bind(
                fileName, CurrentUserContext.userId(), refType.getValue(), refId, LocalDateTime.now());
        return updated > 0;
    }

    private boolean canBind(StoredFile file, FileReferenceType refType, Integer refId) {
        if (Objects.equals(file.getStatus(), StoredFileStatus.TEMPORARY.getStatus())) {
            return Objects.equals(file.getUploaderId(), CurrentUserContext.userId());
        }
        return Objects.equals(file.getStatus(), StoredFileStatus.BOUND.getStatus())
                && Objects.equals(file.getRefType(), refType.getValue())
                && Objects.equals(file.getRefId(), refId);
    }

    private void markDeletePending(List<StoredFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        List<String> names = files.stream().map(StoredFile::getFileName).collect(Collectors.toList());
        if (storedFileMapper.markDeletePending(names, LocalDateTime.now()) != names.size()) {
            throw new IllegalStateException("文件引用状态已变化");
        }
        for (String fileName : names) {
            TransactionCallbacks.afterCommit(() -> {
                LocalDateTime now = LocalDateTime.now();
                StoredFile claimed = claimForCleanup(
                        fileName,
                        now.minusHours(temporaryRetentionHours),
                        now.minusMinutes(DELETION_LEASE_MINUTES),
                        now);
                if (claimed != null) {
                    deleteStoredFile(claimed);
                }
            });
        }
    }

    private boolean deleteStoredFile(StoredFile storedFile) {
        try {
            Path target = resolveTarget(storedFile.getFileName());
            if (target != null) {
                Files.deleteIfExists(target);
            }
            return deleteMetadataInNewTransaction(storedFile.getFileName());
        } catch (Exception e) {
            log.warn("文件删除失败，等待下次重试: fileName={}, error={}",
                    storedFile.getFileName(), e.getMessage());
            return false;
        }
    }

    private StoredFile claimUnboundForDeletion(String fileName, LocalDateTime now) {
        TransactionTemplate transactionTemplate = requiresNewTransactionTemplate();
        return transactionTemplate.execute(status -> {
            if (storedFileMapper.claimUnboundForDeletion(fileName, now) == 0) {
                return null;
            }
            StoredFile claimed = storedFileMapper.selectById(fileName);
            if (claimed == null
                    || !Objects.equals(claimed.getStatus(), StoredFileStatus.DELETING.getStatus())) {
                status.setRollbackOnly();
                return null;
            }
            return claimed;
        });
    }

    private StoredFile claimForCleanup(String fileName,
                                       LocalDateTime temporaryCutoff,
                                       LocalDateTime deletingLeaseCutoff,
                                       LocalDateTime now) {
        TransactionTemplate transactionTemplate = requiresNewTransactionTemplate();
        return transactionTemplate.execute(status -> {
            if (storedFileMapper.claimForCleanup(
                    fileName, temporaryCutoff, deletingLeaseCutoff, now) == 0) {
                return null;
            }
            StoredFile claimed = storedFileMapper.selectById(fileName);
            if (claimed == null
                    || !Objects.equals(claimed.getStatus(), StoredFileStatus.DELETING.getStatus())) {
                status.setRollbackOnly();
                return null;
            }
            return claimed;
        });
    }

    private boolean deleteMetadataInNewTransaction(String fileName) {
        TransactionTemplate transactionTemplate = requiresNewTransactionTemplate();
        Boolean deleted = transactionTemplate.execute(status -> {
            int affected = storedFileMapper.deleteById(fileName);
            return affected == 1 || storedFileMapper.selectById(fileName) == null;
        });
        return Boolean.TRUE.equals(deleted);
    }

    private TransactionTemplate requiresNewTransactionTemplate() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate;
    }

    private boolean isPubliclyAccessible(StoredFile storedFile, FileRule rule) {
        if (!rule.publicPreview) {
            return false;
        }
        if (storedFile == null) {
            return false;
        }
        if (Objects.equals(storedFile.getStatus(), StoredFileStatus.TEMPORARY.getStatus())) {
            return true;
        }
        if (!Objects.equals(storedFile.getStatus(), StoredFileStatus.BOUND.getStatus())) {
            return false;
        }
        FileReferenceType refType = FileReferenceType.fromValue(storedFile.getRefType());
        return refType != null && refType.isPublicAccess();
    }

    private boolean canDownload(StoredFile storedFile) {
        if (storedFile == null) {
            return false;
        }
        Integer userId = CurrentUserContext.userId();
        if (Objects.equals(storedFile.getStatus(), StoredFileStatus.TEMPORARY.getStatus())) {
            return Objects.equals(storedFile.getUploaderId(), userId);
        }
        if (!Objects.equals(storedFile.getStatus(), StoredFileStatus.BOUND.getStatus())) {
            return false;
        }
        if (CurrentUserContext.isAdministrator()) {
            return true;
        }
        if (FileReferenceType.MESSAGE_ATTACHMENT.getValue().equals(storedFile.getRefType())) {
            UserRole role = UserRole.fromCode(CurrentUserContext.roleCode()).orElse(null);
            return role == UserRole.READER
                    || role == UserRole.ADMIN
                    || role == UserRole.SUPER_ADMIN;
        }
        FileReferenceType refType = FileReferenceType.fromValue(storedFile.getRefType());
        return refType != null && refType.isPublicAccess();
    }

    private FileRule validateRequestFile(String fileName, HttpServletResponse response) {
        if (!isSafeFileName(fileName)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        String extension = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
        FileRule rule = FILE_RULES.get(extension);
        if (rule == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        Path target = resolveTarget(fileName);
        if (target == null || !Files.isRegularFile(target)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        return rule;
    }

    private void write(String fileName,
                       StoredFile storedFile,
                       String contentType,
                       boolean forceDownload,
                       HttpServletResponse response) throws IOException {
        Path target = resolveTarget(fileName);
        if (target == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        response.setContentType(contentType);
        if (forceDownload) {
            String originalName = storedFile == null || storedFile.getOriginalName() == null
                    ? fileName : storedFile.getOriginalName();
            response.setHeader("Content-Disposition",
                    ContentDisposition.attachment()
                            .filename(originalName, StandardCharsets.UTF_8)
                            .build().toString());
        }
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", forceDownload ? "no-store" : "public, max-age=3600");
        Files.copy(target, response.getOutputStream());
        response.getOutputStream().flush();
    }

    private Set<String> extractFileNames(String content) {
        Set<String> names = new HashSet<>();
        if (content == null || content.isBlank()) {
            return names;
        }
        Matcher matcher = FILE_NAME_IN_URL.matcher(content);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private String extractFileName(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }
        Matcher matcher = FILE_NAME_IN_URL.matcher(fileUrl.trim());
        return matcher.find() ? matcher.group(1) : null;
    }

    private Path uploadRoot() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    private Path resolveTarget(String fileName) {
        if (!isSafeFileName(fileName)) {
            return null;
        }
        Path root = uploadRoot();
        Path target = root.resolve(fileName).normalize();
        return target.startsWith(root) ? target : null;
    }

    private boolean isOlderThan(Path path, LocalDateTime cutoff) {
        try {
            Instant instant = Files.getLastModifiedTime(path).toInstant();
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).isBefore(cutoff);
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isSafeFileName(String fileName) {
        return fileName != null && SAFE_FILE_NAME.matcher(fileName).matches();
    }

    private String safeOriginalFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.contains("\0")) {
            return null;
        }
        if (originalFilename.contains("..") || originalFilename.contains("/")
                || originalFilename.contains("\\")) {
            return null;
        }
        try {
            return Paths.get(originalFilename).getFileName().toString();
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? "application/octet-stream"
                : contentType.split(";")[0].trim().toLowerCase();
    }

    private boolean hasExpectedFileSignature(MultipartFile multipartFile, String extension) throws IOException {
        byte[] header = readHeader(multipartFile, 512);
        if (header.length == 0) {
            return false;
        }
        switch (extension) {
            case ".jpg":
            case ".jpeg":
                return startsWith(header, 0xFF, 0xD8, 0xFF);
            case ".png":
                return startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case ".gif":
                return startsWithAscii(header, "GIF87a") || startsWithAscii(header, "GIF89a");
            case ".bmp":
                return startsWithAscii(header, "BM");
            case ".webp":
                return startsWithAscii(header, "RIFF") && asciiAt(header, 8, "WEBP");
            case ".pdf":
                return startsWithAscii(header, "%PDF");
            case ".html":
            case ".htm":
                return looksLikeHtml(header);
            case ".doc":
            case ".xls":
            case ".ppt":
                return startsWith(header, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
            case ".docx":
            case ".xlsx":
            case ".pptx":
                return startsWithAscii(header, "PK");
            case ".mp4":
            case ".mov":
                return asciiAt(header, 4, "ftyp");
            case ".avi":
                return startsWithAscii(header, "RIFF") && asciiAt(header, 8, "AVI ");
            case ".wmv":
                return startsWith(header, 0x30, 0x26, 0xB2, 0x75, 0x8E, 0x66, 0xCF, 0x11);
            default:
                return false;
        }
    }

    private byte[] readHeader(MultipartFile multipartFile, int maxBytes) throws IOException {
        try (InputStream inputStream = multipartFile.getInputStream()) {
            byte[] buffer = new byte[maxBytes];
            int length = inputStream.read(buffer);
            if (length <= 0) {
                return new byte[0];
            }
            byte[] actual = new byte[length];
            System.arraycopy(buffer, 0, actual, 0, length);
            return actual;
        }
    }

    private boolean looksLikeHtml(byte[] header) {
        String prefix = new String(header, StandardCharsets.UTF_8)
                .replace("\uFEFF", "")
                .trim()
                .toLowerCase();
        return prefix.startsWith("<")
                && (prefix.contains("<html") || prefix.contains("<!doctype html")
                || prefix.contains("<head") || prefix.contains("<body"));
    }

    private boolean startsWith(byte[] data, int... expected) {
        if (data.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean startsWithAscii(byte[] data, String expected) {
        return asciiAt(data, 0, expected);
    }

    private boolean asciiAt(byte[] data, int offset, String expected) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.US_ASCII);
        if (data.length < offset + expectedBytes.length) {
            return false;
        }
        for (int i = 0; i < expectedBytes.length; i++) {
            if (data[offset + i] != expectedBytes[i]) {
                return false;
            }
        }
        return true;
    }

    private static void putRule(String extension,
                                long maxSize,
                                boolean publicPreview,
                                String... contentTypes) {
        FILE_RULES.put(extension,
                new FileRule(contentTypes[0], Set.of(contentTypes), maxSize, publicPreview));
    }

    private static class FileRule {
        private final String responseContentType;
        private final Set<String> contentTypes;
        private final long maxSize;
        private final boolean publicPreview;

        private FileRule(String responseContentType,
                         Set<String> contentTypes,
                         long maxSize,
                         boolean publicPreview) {
            this.responseContentType = responseContentType;
            this.contentTypes = contentTypes;
            this.maxSize = maxSize;
            this.publicPreview = publicPreview;
        }

        private boolean allowsContentType(String contentType) {
            if (contentType == null || contentType.trim().isEmpty()) {
                return false;
            }
            String normalized = contentType.split(";")[0].trim().toLowerCase();
            return OCTET_STREAM_TYPES.contains(normalized) || contentTypes.contains(normalized);
        }
    }
}
