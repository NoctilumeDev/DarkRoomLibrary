package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.mapper.StoredFileMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.StoredFilePageQuery;
import org.darkroomlibrary.domain.type.FileReferenceType;
import org.darkroomlibrary.domain.type.StoredFileStatus;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.domain.model.StoredFile;
import org.darkroomlibrary.web.view.StoredFileView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.transaction.PlatformTransactionManager;

class FileStorageServiceImplTest {

    private static final String FILE_NAME = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.png";
    private static final String FILE_URL = "/api/test/file/public?fileName=" + FILE_NAME;

    @TempDir
    private Path tempDir;

    private FileStorageServiceImpl service;
    private StoredFileMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = mock(StoredFileMapper.class);
        service = new FileStorageServiceImpl();
        ReflectionTestUtils.setField(service, "apiPrefix", "/api/test");
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "temporaryRetentionHours", 24L);
        ReflectionTestUtils.setField(service, "storedFileMapper", mapper);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        ReflectionTestUtils.setField(service, "transactionManager", transactionManager);
        CurrentUserContext.bind(7, 2);
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void bindsTemporaryFileToBusinessReference() {
        StoredFile temporary = temporaryFile(7);
        when(mapper.selectById(FILE_NAME)).thenReturn(temporary);
        when(mapper.findByReference("book_cover", 12)).thenReturn(Collections.emptyList());
        when(mapper.bind(eq(FILE_NAME), eq(7), eq("book_cover"), eq(12), any())).thenReturn(1);

        assertTrue(service.bindSingle(FILE_URL, FileReferenceType.BOOK_COVER, 12));
        verify(mapper).bind(eq(FILE_NAME), eq(7), eq("book_cover"), eq(12), any());
    }

    @Test
    void rejectsBindingAnotherUsersTemporaryFile() {
        when(mapper.selectById(FILE_NAME)).thenReturn(temporaryFile(8));

        assertFalse(service.bindSingle(FILE_URL, FileReferenceType.USER_AVATAR, 7));
    }

    @Test
    void deletesUnboundFileFromMetadataAndDisk() throws Exception {
        Files.write(tempDir.resolve(FILE_NAME), new byte[]{1, 2, 3});
        StoredFile temporary = temporaryFile(7);
        StoredFile deleting = temporaryFile(7);
        deleting.setStatus(StoredFileStatus.DELETING.getStatus());
        when(mapper.selectById(FILE_NAME)).thenReturn(temporary, deleting);
        when(mapper.claimUnboundForDeletion(eq(FILE_NAME), any())).thenReturn(1);
        when(mapper.deleteById(FILE_NAME)).thenReturn(1);

        ApiResponse<Void> result = service.deleteUnbound(FILE_NAME);

        assertEquals(200, result.getCode());
        assertFalse(Files.exists(tempDir.resolve(FILE_NAME)));
        verify(mapper).deleteById(FILE_NAME);
    }

    @Test
    void doesNotDeleteFileThatWasBoundConcurrently() throws Exception {
        Files.write(tempDir.resolve(FILE_NAME), new byte[]{1, 2, 3});
        when(mapper.selectById(FILE_NAME)).thenReturn(temporaryFile(7));
        when(mapper.claimUnboundForDeletion(eq(FILE_NAME), any())).thenReturn(0);

        ApiResponse<Void> result = service.deleteUnbound(FILE_NAME);

        assertEquals(400, result.getCode());
        assertTrue(Files.exists(tempDir.resolve(FILE_NAME)));
        verify(mapper, never()).deleteById(FILE_NAME);
    }

    @Test
    void cleanupSkipsTemporaryFileThatWasBoundConcurrently() throws Exception {
        Files.write(tempDir.resolve(FILE_NAME), new byte[]{1, 2, 3});
        StoredFile candidate = temporaryFile(7);
        when(mapper.findCleanupCandidates(any(), any(), eq(200))).thenReturn(List.of(candidate));
        when(mapper.claimForCleanup(eq(FILE_NAME), any(), any(), any())).thenReturn(0);

        ApiResponse<java.util.Map<String, Object>> result = service.cleanupNow();

        assertEquals(0, result.getData().get("metadataDeleted"));
        assertTrue(Files.exists(tempDir.resolve(FILE_NAME)));
        verify(mapper, never()).deleteById(FILE_NAME);
    }

    @Test
    void cleanupRemovesOldUntrackedDiskFile() throws Exception {
        Path orphan = tempDir.resolve(FILE_NAME);
        Files.write(orphan, new byte[]{1, 2, 3});
        Files.setLastModifiedTime(orphan, FileTime.from(Instant.now().minusSeconds(48 * 3600)));
        when(mapper.findCleanupCandidates(any(), any(), eq(200))).thenReturn(Collections.emptyList());
        when(mapper.selectById(FILE_NAME)).thenReturn(null);
        when(mapper.countLegacyReferences(FILE_NAME)).thenReturn(0);

        ApiResponse<java.util.Map<String, Object>> result = service.cleanupNow();

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().get("diskOrphansDeleted"));
        assertFalse(Files.exists(orphan));
    }

    @Test
    void queryUsesAuthenticatedDownloadForNonPreviewableFiles() {
        StoredFilePageQuery dto = new StoredFilePageQuery();
        StoredFileView file = new StoredFileView();
        file.setFileName("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.pdf");
        file.setExtension("pdf");
        when(mapper.query(dto)).thenReturn(Collections.singletonList(file));
        when(mapper.queryCount(dto)).thenReturn(1);

        ApiResponse<List<StoredFileView>> result = service.query(dto);

        assertEquals(200, result.getCode());
        assertEquals("/api/test/file/download?fileName=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.pdf",
                result.getData().get(0).getAccessUrl());
    }

    @Test
    void rejectsPublicAccessWhenFileMetadataIsMissing() throws Exception {
        Files.write(tempDir.resolve(FILE_NAME), new byte[]{1, 2, 3});
        when(mapper.selectById(FILE_NAME)).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.writePublicFile(FILE_NAME, response);

        assertEquals(403, response.getStatus());
        assertEquals(0, response.getContentAsByteArray().length);
    }

    @Test
    void rejectsDownloadWhenFileMetadataIsMissing() throws Exception {
        Files.write(tempDir.resolve(FILE_NAME), new byte[]{1, 2, 3});
        when(mapper.selectById(FILE_NAME)).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.writeDownload(FILE_NAME, response);

        assertEquals(403, response.getStatus());
        assertEquals(0, response.getContentAsByteArray().length);
    }

    @Test
    void allowsReaderToDownloadMessageAttachment() throws Exception {
        Files.write(tempDir.resolve(FILE_NAME), new byte[]{1, 2, 3});
        when(mapper.selectById(FILE_NAME)).thenReturn(boundMessageAttachment());
        CurrentUserContext.bind(7, UserRole.READER.code());
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.writeDownload(FILE_NAME, response);

        assertEquals(200, response.getStatus());
        assertEquals(3, response.getContentAsByteArray().length);
    }

    @Test
    void rejectsProcurementRolesFromDownloadingMessageAttachment() throws Exception {
        Files.write(tempDir.resolve(FILE_NAME), new byte[]{1, 2, 3});
        when(mapper.selectById(FILE_NAME)).thenReturn(boundMessageAttachment());

        CurrentUserContext.bind(7, UserRole.ACQUISITIONS.code());
        MockHttpServletResponse acquisitionsResponse = new MockHttpServletResponse();
        service.writeDownload(FILE_NAME, acquisitionsResponse);

        CurrentUserContext.bind(8, UserRole.LOGISTICS.code());
        MockHttpServletResponse logisticsResponse = new MockHttpServletResponse();
        service.writeDownload(FILE_NAME, logisticsResponse);

        assertEquals(403, acquisitionsResponse.getStatus());
        assertEquals(403, logisticsResponse.getStatus());
    }

    private StoredFile temporaryFile(Integer uploaderId) {
        return StoredFile.builder()
                .fileName(FILE_NAME)
                .uploaderId(uploaderId)
                .status(StoredFileStatus.TEMPORARY.getStatus())
                .build();
    }

    private StoredFile boundMessageAttachment() {
        return StoredFile.builder()
                .fileName(FILE_NAME)
                .originalName("message.png")
                .status(StoredFileStatus.BOUND.getStatus())
                .refType(FileReferenceType.MESSAGE_ATTACHMENT.getValue())
                .refId(1)
                .build();
    }
}
