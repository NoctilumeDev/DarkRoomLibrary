package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.MessageBoardPageQuery;
import org.darkroomlibrary.domain.model.MessageBoard;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.web.view.MessageBoardView;
import org.darkroomlibrary.service.MessageBoardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import jakarta.annotation.Resource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MessageBoardServiceImplTest extends BaseTest {

    @Resource
    private MessageBoardService messageBoardService;

    private static int userIndex = 0;

    private User testUser;

    @BeforeEach
    void setUp() {
        clearContext();
        userIndex++;
        testUser = createTestUser(
                "messageuser" + userIndex,
                "留言测试用户" + userIndex,
                "message" + userIndex + "@test.com"
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
}
