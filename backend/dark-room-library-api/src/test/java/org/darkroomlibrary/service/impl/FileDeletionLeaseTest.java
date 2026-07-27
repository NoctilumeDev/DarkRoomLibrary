package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.domain.model.StoredFile;
import org.darkroomlibrary.domain.type.FileReferenceType;
import org.darkroomlibrary.domain.type.StoredFileStatus;
import org.darkroomlibrary.mapper.StoredFileMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FileDeletionLeaseTest extends BaseTest {

    private static final String FILE_NAME = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.png";
    private static final String AFTER_COMMIT_FILE_NAME = "cccccccccccccccccccccccccccccccc.png";

    @Resource
    private StoredFileMapper storedFileMapper;

    @Resource
    private FileStorageServiceImpl fileStorageService;

    @Resource
    private PlatformTransactionManager transactionManager;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(fileStorageService, "uploadDir", tempDir.toString());
    }

    @Test
    void deletionClaimIsExclusiveAndExpiredLeaseCanBeRecovered() {
        LocalDateTime now = LocalDateTime.now();
        StoredFile file = StoredFile.builder()
                .fileName(FILE_NAME)
                .originalName("lease.png")
                .extension("png")
                .contentType("image/png")
                .fileSize(3L)
                .status(StoredFileStatus.DELETE_PENDING.getStatus())
                .createTime(now.minusHours(2))
                .updateTime(now.minusHours(1))
                .build();
        assertEquals(1, storedFileMapper.insert(file));

        LocalDateTime temporaryCutoff = now.minusHours(24);
        LocalDateTime leaseCutoff = now.minusMinutes(10);
        assertEquals(1, storedFileMapper.claimForCleanup(
                FILE_NAME, temporaryCutoff, leaseCutoff, now));
        assertEquals(0, storedFileMapper.claimForCleanup(
                FILE_NAME, temporaryCutoff, leaseCutoff, now.plusSeconds(1)));
        assertEquals(StoredFileStatus.DELETING.getStatus(),
                storedFileMapper.selectById(FILE_NAME).getStatus());

        StoredFile expiredLease = StoredFile.builder()
                .fileName(FILE_NAME)
                .status(StoredFileStatus.DELETING.getStatus())
                .updateTime(now.minusMinutes(20))
                .build();
        assertEquals(1, storedFileMapper.updateById(expiredLease));

        List<StoredFile> candidates = storedFileMapper.findCleanupCandidates(
                temporaryCutoff, leaseCutoff, 20);
        assertTrue(candidates.stream().anyMatch(candidate -> FILE_NAME.equals(candidate.getFileName())));
        assertEquals(1, storedFileMapper.claimForCleanup(
                FILE_NAME, temporaryCutoff, leaseCutoff, now.plusSeconds(2)));
    }

    @Test
    void afterCommitCleanupUsesIndependentTransactions() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Path path = tempDir.resolve(AFTER_COMMIT_FILE_NAME);
        Files.write(path, new byte[]{1, 2, 3});
        StoredFile file = StoredFile.builder()
                .fileName(AFTER_COMMIT_FILE_NAME)
                .originalName("after-commit.png")
                .extension("png")
                .contentType("image/png")
                .fileSize(3L)
                .status(StoredFileStatus.BOUND.getStatus())
                .refType(FileReferenceType.BOOK_COVER.getValue())
                .refId(9876)
                .createTime(now)
                .updateTime(now)
                .build();
        assertEquals(1, storedFileMapper.insert(file));

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status ->
                fileStorageService.releaseReference(FileReferenceType.BOOK_COVER, 9876));

        assertNull(storedFileMapper.selectById(AFTER_COMMIT_FILE_NAME));
        assertFalse(Files.exists(path));
    }
}
