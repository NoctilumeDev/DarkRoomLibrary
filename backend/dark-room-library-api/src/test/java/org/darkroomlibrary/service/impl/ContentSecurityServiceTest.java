package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.BookReviewPageQuery;
import org.darkroomlibrary.web.dto.query.MessageBoardPageQuery;
import org.darkroomlibrary.web.dto.query.NoticePageQuery;
import org.darkroomlibrary.web.dto.query.ProcurementMessagePageQuery;
import org.darkroomlibrary.web.dto.query.ProcurementOrderPageQuery;
import org.darkroomlibrary.web.dto.command.ProcurementMessageDto;
import org.darkroomlibrary.web.dto.command.ProcurementOrderCreateDto;
import org.darkroomlibrary.web.dto.command.BookReviewCreateDto;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.domain.model.MessageBoard;
import org.darkroomlibrary.domain.model.Notice;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.web.view.BookReviewView;
import org.darkroomlibrary.web.view.MessageBoardView;
import org.darkroomlibrary.web.view.ProcurementMessageView;
import org.darkroomlibrary.web.view.ProcurementOrderView;
import org.darkroomlibrary.service.BookReviewService;
import org.darkroomlibrary.service.MessageBoardService;
import org.darkroomlibrary.service.NoticeService;
import org.darkroomlibrary.service.ProcurementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import jakarta.annotation.Resource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ContentSecurityServiceTest extends BaseTest {

    @Resource
    private MessageBoardService messageBoardService;
    @Resource
    private BookReviewService bookReviewService;
    @Resource
    private NoticeService noticeService;
    @Resource
    private ProcurementService procurementService;

    @AfterEach
    void tearDown() {
        clearContext();
    }

    @Test
    @DisplayName("留言、回复和附件元数据执行长度校验与纯文本净化")
    void sanitizeMessageBoardContent() {
        User reader = createTestUser(
                "content_reader_01", "内容读者一", "content_reader_01@test.com");
        setCurrentUser(reader.getId(), reader.getUserRole());

        MessageBoard message = MessageBoard.builder()
                .content("<script>alert(1)</script><b>正常留言</b>")
                .attachmentUrl("/api/dark-room-library/v1/file/getFile?fileName=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.pdf")
                .attachmentName("<img src=x onerror=alert(1)>报告.pdf")
                .attachmentType("pdf")
                .build();
        assertEquals(200, messageBoardService.save(message).getCode());

        MessageBoardPageQuery query = new MessageBoardPageQuery();
        query.setCurrent(0);
        query.setSize(10);
        MessageBoardView saved = messageBoardService.query(query).getData().get(0);
        assertEquals("正常留言", saved.getContent());
        assertEquals("报告.pdf", saved.getAttachmentName());

        assertEquals(200, messageBoardService.reply(
                saved.getId(), "<script>alert(1)</script><i>安全回复</i>").getCode());
        MessageBoardView replied = messageBoardService.query(query).getData().stream()
                .filter(item -> saved.getId().equals(item.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals("安全回复", replied.getReply());

        MessageBoard tooLong = MessageBoard.builder().content("长".repeat(1001)).build();
        assertEquals(400, messageBoardService.save(tooLong).getCode());
        assertEquals(400, messageBoardService.reply(saved.getId(), "长".repeat(1001)).getCode());

        MessageBoard dangerousAttachment = MessageBoard.builder()
                .content("附件测试")
                .attachmentUrl("javascript:alert(1)")
                .attachmentName("危险.html")
                .attachmentType("html")
                .build();
        assertEquals(400, messageBoardService.save(dangerousAttachment).getCode());
    }

    @Test
    @DisplayName("书评和回复移除脚本并拒绝超长输入")
    void sanitizeBookReviewContent() {
        User reader = createTestUser(
                "content_reader_02", "内容读者二", "content_reader_02@test.com");
        Book book = createTestBook("内容安全测试图书", "安全作者", 1);
        setCurrentUser(reader.getId(), reader.getUserRole());

        BookReviewCreateDto review = BookReviewCreateDto.builder()
                .bookId(book.getId())
                .rating(5)
                .content("<img src=x onerror=alert(1)><strong>安全书评</strong>")
                .build();
        assertEquals(200, bookReviewService.save(review).getCode());

        BookReviewPageQuery query = new BookReviewPageQuery();
        query.setBookId(book.getId());
        query.setCurrent(0);
        query.setSize(10);
        Integer reviewId = bookReviewService.query(query).getData().get(0).getId();
        assertEquals(200, bookReviewService.reply(
                reviewId, "<script>alert(1)</script><em>安全回复</em>", reader.getId()).getCode());

        BookReviewView saved = bookReviewService.query(query).getData().get(0);
        assertEquals("安全书评", saved.getContent());
        assertEquals("安全回复", saved.getReplies().get(0).getContent());

        BookReviewCreateDto tooLong = BookReviewCreateDto.builder()
                .bookId(book.getId())
                .rating(5)
                .content("长".repeat(1001))
                .build();
        assertEquals(400, bookReviewService.save(tooLong).getCode());
        assertEquals(400, bookReviewService.reply(
                reviewId, "长".repeat(501), reader.getId()).getCode());
    }

    @Test
    @DisplayName("公告保留安全富文本并移除脚本和事件属性")
    void sanitizeNoticeRichText() {
        Notice notice = new Notice();
        notice.setName("<b>安全公告</b>");
        notice.setContent("<p onclick=\"alert(1)\">公告<strong>正文</strong></p>"
                + "<script>alert(1)</script>"
                + "<img src=\"javascript:alert(1)\" onerror=\"alert(1)\">");

        assertEquals(200, noticeService.save(notice).getCode());

        NoticePageQuery query = new NoticePageQuery();
        query.setCurrent(0);
        query.setSize(10);
        Notice saved = noticeService.query(query).getData().get(0);
        assertEquals("安全公告", saved.getName());
        assertTrue(saved.getContent().contains("<strong>正文</strong>"));
        assertFalse(saved.getContent().contains("script"));
        assertFalse(saved.getContent().contains("onclick"));
        assertFalse(saved.getContent().contains("onerror"));
        assertFalse(saved.getContent().contains("javascript:"));

        Notice tooLong = new Notice();
        tooLong.setName("超长公告");
        tooLong.setContent("长".repeat(20001));
        assertEquals(400, noticeService.save(tooLong).getCode());

        Notice scriptOnly = new Notice();
        scriptOnly.setName("空公告");
        scriptOnly.setContent("<script>alert(1)</script>");
        assertEquals(400, noticeService.save(scriptOnly).getCode());
    }

    @Test
    @DisplayName("采购协作消息执行纯文本净化和长度限制")
    void sanitizeProcurementMessage() {
        User admin = createRoleUser(
                "content_admin_01", "内容管理员", UserRole.ADMIN.code());
        User purchaser = createRoleUser(
                "content_buyer_01", "内容采购员", UserRole.ACQUISITIONS.code());
        Book book = createTestBook("采购消息安全图书", "采购作者", 1);
        setCurrentUser(admin.getId(), admin.getUserRole());

        ProcurementOrderCreateDto create = new ProcurementOrderCreateDto();
        create.setBookId(book.getId());
        create.setRequestCount(2);
        create.setPurchaserId(purchaser.getId());
        assertEquals(200, procurementService.save(create).getCode());

        ProcurementOrderPageQuery orderQuery = new ProcurementOrderPageQuery();
        orderQuery.setBookId(book.getId());
        orderQuery.setCurrent(0);
        orderQuery.setSize(10);
        ProcurementOrderView order = procurementService.query(orderQuery).getData().get(0);

        ProcurementMessageDto message = new ProcurementMessageDto();
        message.setOrderId(order.getId());
        message.setChannelType(0);
        message.setReceiverId(purchaser.getId());
        message.setContent("<script>alert(1)</script><b>安全采购消息</b>");
        assertEquals(200, procurementService.sendMessage(message).getCode());

        ProcurementMessagePageQuery messageQuery = new ProcurementMessagePageQuery();
        messageQuery.setOrderId(order.getId());
        messageQuery.setChannelType(0);
        messageQuery.setCurrent(0);
        messageQuery.setSize(10);
        List<ProcurementMessageView> messages = procurementService.queryMessages(messageQuery).getData();
        assertEquals("安全采购消息", messages.get(0).getContent());

        message.setContent("长".repeat(1001));
        assertEquals(400, procurementService.sendMessage(message).getCode());
    }

    private User createRoleUser(String account, String userName, Integer role) {
        User user = createTestUser(account, userName, account + "@test.com");
        userMapper.update(User.builder().id(user.getId()).userRole(role).build());
        user.setUserRole(role);
        return user;
    }
}
