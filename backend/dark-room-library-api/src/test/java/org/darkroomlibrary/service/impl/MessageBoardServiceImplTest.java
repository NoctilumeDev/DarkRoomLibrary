package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.MessageBoardPageQuery;
import org.darkroomlibrary.domain.model.MessageBoard;
import org.darkroomlibrary.domain.model.StoredFile;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.mapper.MessageBoardMapper;
import org.darkroomlibrary.mapper.StoredFileMapper;
import org.darkroomlibrary.domain.type.FileReferenceType;
import org.darkroomlibrary.domain.type.StoredFileStatus;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.web.dto.query.PageQuery;
import org.darkroomlibrary.web.view.MessageBoardView;
import org.darkroomlibrary.service.MessageBoardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MessageBoardServiceImplTest extends BaseTest {

    @Resource
    private MessageBoardService messageBoardService;

    @Resource
    private MessageBoardMapper messageBoardMapper;

    @Resource
    private StoredFileMapper storedFileMapper;

    @Resource
    private RecycleBinExpiryService recycleBinExpiryService;

    private static int userIndex = 0;

    private User testUser;

    @BeforeEach
    void setUp() {
        clearContext();
        userIndex++;
        testUser = createTestUser(
                "messageuser" + userIndex,
                "留言测试用户" + userIndex,
                "message" + userIndex + "@example.test"
        );
        setCurrentUser(testUser.getId(), testUser.getUserRole());
    }

    @Test
    @DisplayName("留言成功 - 仅上传附件")
    void testSaveAttachmentOnlyMessage() {
        MessageBoard messageBoard = MessageBoard.builder()
                .content("")
                .attachmentUrl("/api/dark-room-library/v1/file/getFile?fileName=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.pdf")
                .attachmentName("测试附件.pdf")
                .attachmentType("pdf")
                .build();

        ApiResponse<Void> result = messageBoardService.save(messageBoard);
        assertNotNull(result);
        assertEquals(200, result.getCode());

        MessageBoardPageQuery queryDto = new MessageBoardPageQuery();
        queryDto.setCurrent(0);
        queryDto.setSize(10);
        List<MessageBoardView> messages = messageBoardService.query(queryDto).getData();
        assertNotNull(messages);
        assertFalse(messages.isEmpty());
        assertEquals("测试附件.pdf", messages.get(0).getAttachmentName());
        assertEquals("pdf", messages.get(0).getAttachmentType());
    }

    @Test
    @DisplayName("留言失败 - 内容和附件不能同时为空")
    void testRejectBlankMessageWithoutAttachment() {
        MessageBoard messageBoard = MessageBoard.builder()
                .content(" ")
                .build();

        ApiResponse<Void> result = messageBoardService.save(messageBoard);
        assertNotNull(result);
        assertEquals(400, result.getCode());
    }

    @Test
    @DisplayName("已禁言读者不能发布留言")
    void testMutedReaderCannotPostMessage() {
        userMapper.update(User.builder().id(testUser.getId()).isWord(true).build());

        ApiResponse<Void> result = messageBoardService.save(MessageBoard.builder()
                .content("这条留言不应被保存")
                .build());

        assertEquals(400, result.getCode());
        assertEquals("当前账号已被禁言，暂不能发布或修改内容", result.getMsg());
    }

    @Test
    @DisplayName("留言回复成功 - 查询列表返回管理员回复")
    void testReplyMessage() {
        MessageBoard messageBoard = MessageBoard.builder()
                .content("请问图书什么时候补货？")
                .build();

        ApiResponse<Void> saveResult = messageBoardService.save(messageBoard);
        assertNotNull(saveResult);
        assertEquals(200, saveResult.getCode());

        MessageBoardPageQuery queryDto = new MessageBoardPageQuery();
        queryDto.setCurrent(0);
        queryDto.setSize(10);
        List<MessageBoardView> messages = messageBoardService.query(queryDto).getData();
        assertFalse(messages.isEmpty());

        Integer messageId = messages.get(0).getId();
        ApiResponse<Void> replyResult = messageBoardService.reply(messageId, "已记录，会在本周补充。");
        assertNotNull(replyResult);
        assertEquals(200, replyResult.getCode());

        List<MessageBoardView> repliedMessages = messageBoardService.query(queryDto).getData();
        MessageBoardView replied = repliedMessages.stream()
                .filter(item -> messageId.equals(item.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals("已记录，会在本周补充。", replied.getReply());
    }

    @Test
    @DisplayName("读者留言进入回收站后可恢复，过期后自动失去恢复资格")
    void testReaderMessageRecycleLifecycle() {
        MessageBoard message = MessageBoard.builder().content("请暂时收起这条留言").build();
        assertEquals(200, messageBoardService.save(message).getCode());

        assertEquals(200, messageBoardService.batchDelete(List.of(message.getId())).getCode());
        PageQuery page = new PageQuery();
        page.setCurrent(0);
        page.setSize(10);
        assertTrue(messageBoardService.queryRecycleBin(page).getData().stream()
                .anyMatch(item -> message.getId().equals(item.getId())));
        assertEquals(200, messageBoardService.restore(List.of(message.getId())).getCode());
        assertFalse(Boolean.TRUE.equals(messageBoardMapper.getById(message.getId()).getIsDeleted()));

        assertEquals(200, messageBoardService.batchDelete(List.of(message.getId())).getCode());
        messageBoardMapper.update(MessageBoard.builder()
                .id(message.getId())
                .restoreDeadline(java.time.LocalDateTime.now().minusSeconds(1))
                .build());
        recycleBinExpiryService.cleanupExpiredEntries();

        assertNotNull(messageBoardMapper.getById(message.getId()).getExpiredAt());
        assertEquals(400, messageBoardService.restore(List.of(message.getId())).getCode());
        assertTrue(messageBoardService.queryRecycleBin(page).getData().stream()
                .noneMatch(item -> message.getId().equals(item.getId())));
    }

    @Test
    @DisplayName("读者留言附件在恢复窗口内保留，恢复资格到期后才释放")
    void testReaderMessageAttachmentFollowsRecycleLifecycle() {
        MessageBoard message = MessageBoard.builder().content("带附件的回收站留言").build();
        assertEquals(200, messageBoardService.save(message).getCode());

        String fileName = String.format("%032x.pdf", message.getId());
        LocalDateTime now = LocalDateTime.now();
        assertEquals(1, storedFileMapper.insert(StoredFile.builder()
                .fileName(fileName)
                .originalName("回收站附件.pdf")
                .extension("pdf")
                .contentType("application/pdf")
                .fileSize(3L)
                .uploaderId(testUser.getId())
                .status(StoredFileStatus.BOUND.getStatus())
                .refType(FileReferenceType.MESSAGE_ATTACHMENT.getValue())
                .refId(message.getId())
                .createTime(now)
                .bindTime(now)
                .updateTime(now)
                .build()));

        assertEquals(200, messageBoardService.batchDelete(List.of(message.getId())).getCode());
        assertNotNull(storedFileMapper.selectById(fileName), "进入回收站不应释放附件");

        assertEquals(200, messageBoardService.restore(List.of(message.getId())).getCode());
        assertNotNull(storedFileMapper.selectById(fileName), "恢复留言后附件引用应继续存在");

        assertEquals(200, messageBoardService.batchDelete(List.of(message.getId())).getCode());
        messageBoardMapper.update(MessageBoard.builder()
                .id(message.getId())
                .restoreDeadline(LocalDateTime.now().minusSeconds(1))
                .build());
        recycleBinExpiryService.cleanupExpiredEntries();

        assertNotNull(messageBoardMapper.getById(message.getId()).getExpiredAt());
        assertNull(storedFileMapper.selectById(fileName), "恢复资格到期后应释放附件元数据");
    }

    @Test
    @DisplayName("管理员移出公开留言不会授予读者恢复权")
    void testAdministratorModerationIsNotReaderTrash() {
        MessageBoard message = MessageBoard.builder().content("需要管理员治理的留言").build();
        assertEquals(200, messageBoardService.save(message).getCode());
        setCurrentUser(testUser.getId(), UserRole.ADMIN.code());

        assertEquals(200, messageBoardService.batchDelete(List.of(message.getId())).getCode());
        MessageBoard governed = messageBoardMapper.getById(message.getId());
        assertEquals(1, governed.getModerationStatus());
        assertFalse(Boolean.TRUE.equals(governed.getIsDeleted()));

        setCurrentUser(testUser.getId(), UserRole.READER.code());
        PageQuery page = new PageQuery();
        page.setCurrent(0);
        page.setSize(10);
        assertTrue(messageBoardService.queryRecycleBin(page).getData().stream()
                .noneMatch(item -> message.getId().equals(item.getId())));
        assertEquals(400, messageBoardService.restore(List.of(message.getId())).getCode());
    }

    @Test
    @DisplayName("管理员迟到的治理请求不能覆盖读者已经建立的留言回收状态")
    void testStaleAdministratorModerationCannotOverrideReaderRecycleState() {
        MessageBoard message = MessageBoard.builder().content("先由读者移入回收站的留言").build();
        assertEquals(200, messageBoardService.save(message).getCode());
        assertEquals(200, messageBoardService.batchDelete(List.of(message.getId())).getCode());

        setCurrentUser(testUser.getId(), UserRole.ADMIN.code());
        assertEquals(400, messageBoardService.batchDelete(List.of(message.getId())).getCode());

        MessageBoard retained = messageBoardMapper.getById(message.getId());
        assertEquals(0, retained.getModerationStatus());
        assertTrue(Boolean.TRUE.equals(retained.getIsDeleted()));
        assertNotNull(retained.getRestoreDeadline());
    }
}
