package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.mapper.OperationLogMapper;
import org.darkroomlibrary.mapper.BookReviewMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.BookReviewPageQuery;
import org.darkroomlibrary.web.dto.query.BookReviewReportPageQuery;
import org.darkroomlibrary.web.dto.command.BookReviewCreateDto;
import org.darkroomlibrary.web.dto.command.BookReviewUpdateDto;
import org.darkroomlibrary.domain.model.OperationLog;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.web.view.BookReviewReportView;
import org.darkroomlibrary.web.view.BookReviewView;
import org.darkroomlibrary.service.BookReviewReportService;
import org.darkroomlibrary.service.BookReviewService;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 图书评价服务测试
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BookReviewServiceImplTest extends BaseTest {

    @Resource
    private BookReviewService bookReviewService;
    @Resource
    private BookReviewReportService bookReviewReportService;
    @Resource
    private OperationLogMapper operationLogMapper;
    @Resource
    private BookReviewMapper bookReviewMapper;

    private static Integer bookId;
    private static Integer userId;
    private static Integer reviewId;

    @BeforeEach
    void setUp() {
        clearContext();
    }

    @Test
    @Order(1)
    @DisplayName("新增评价成功")
    void testSaveReviewSuccess() {
        var user = createTestUser("reviewer01", "评书人", "reviewer@test.com");
        var book = createTestBook("测试图书", "作者名", 5);
        userId = user.getId();
        bookId = book.getId();
        setCurrentUser(userId, user.getUserRole());

        BookReviewCreateDto review = BookReviewCreateDto.builder()
                .bookId(bookId)
                .rating(4)
                .content("这本书非常不错！")
                .build();
        ApiResponse<Void> result = bookReviewService.save(review);
        assertEquals(200, result.getCode());
        BookReviewPageQuery queryDto = new BookReviewPageQuery();
        queryDto.setBookId(bookId);
        queryDto.setCurrent(0);
        queryDto.setSize(10);
        reviewId = bookReviewService.query(queryDto).getData().stream()
                .filter(item -> userId.equals(item.getUserId()))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    @Test
    @Order(2)
    @DisplayName("评价评分范围校验-低于1")
    void testSaveReviewRatingTooLow() {
        setCurrentUser(userId, 2);

        BookReviewCreateDto review = BookReviewCreateDto.builder()
                .bookId(bookId)
                .rating(0)
                .content("评分太低")
                .build();
        ApiResponse<Void> result = bookReviewService.save(review);
        assertEquals(400, result.getCode());
    }

    @Test
    @Order(3)
    @DisplayName("评价内容为空校验")
    void testSaveReviewEmptyContent() {
        setCurrentUser(userId, 2);

        BookReviewCreateDto review = BookReviewCreateDto.builder()
                .bookId(bookId)
                .rating(3)
                .content("  ")
                .build();
        ApiResponse<Void> result = bookReviewService.save(review);
        assertEquals(400, result.getCode());
    }

    @Test
    @Order(4)
    @DisplayName("修改自己的评价成功")
    void testUpdateOwnReview() {
        setCurrentUser(userId, 2);

        BookReviewUpdateDto review = BookReviewUpdateDto.builder()
                .id(reviewId)
                .rating(5)
                .content("修改后的评价内容")
                .build();
        ApiResponse<Void> result = bookReviewService.update(review);
        assertEquals(200, result.getCode());
    }

    @Test
    @Order(5)
    @DisplayName("评价点赞与回复成功")
    void testLikeAndReplyReview() {
        setCurrentUser(userId, 2);

        ApiResponse<Boolean> likeResult = bookReviewService.toggleLike(reviewId);
        assertEquals(200, likeResult.getCode());
        assertTrue(likeResult.getData());

        ApiResponse<Void> replyResult = bookReviewService.reply(reviewId, "我也觉得这本书不错。", userId);
        assertEquals(200, replyResult.getCode());

        BookReviewPageQuery queryDto = new BookReviewPageQuery();
        queryDto.setBookId(bookId);
        queryDto.setCurrent(0);
        queryDto.setSize(10);
        queryDto.setSortBy("hot");
        ApiResponse<List<BookReviewView>> queryResult = bookReviewService.query(queryDto);
        assertEquals(200, queryResult.getCode());
        BookReviewView review = queryResult.getData().stream()
                .filter(item -> reviewId.equals(item.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, review.getLikeCount());
        assertTrue(review.getLiked());
        assertFalse(review.getReplies().isEmpty());
        assertEquals("我也觉得这本书不错。", review.getReplies().get(0).getContent());
    }

    @Test
    @Order(6)
    @DisplayName("再次点赞会取消点赞")
    void testToggleLikeCancel() {
        setCurrentUser(userId, 2);

        ApiResponse<Boolean> result = bookReviewService.toggleLike(reviewId);
        assertEquals(200, result.getCode());
        assertFalse(result.getData());
    }

    @Test
    @Order(7)
    @DisplayName("评价举报成功并回显状态")
    void testReportReview() {
        var reporter = createTestUser("reporter01", "举报人", "reporter@test.com");
        setCurrentUser(reporter.getId(), reporter.getUserRole());

        ApiResponse<Void> reportResult = bookReviewService.report(reviewId, "包含不合适内容");
        assertEquals(200, reportResult.getCode());

        ApiResponse<Void> duplicateResult = bookReviewService.report(reviewId, "重复举报");
        assertEquals(400, duplicateResult.getCode());

        BookReviewPageQuery queryDto = new BookReviewPageQuery();
        queryDto.setBookId(bookId);
        queryDto.setCurrent(0);
        queryDto.setSize(10);
        queryDto.setSortBy("latest");
        ApiResponse<List<BookReviewView>> queryResult = bookReviewService.query(queryDto);
        assertEquals(200, queryResult.getCode());
        BookReviewView review = queryResult.getData().stream()
                .filter(item -> reviewId.equals(item.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, review.getReportCount());
        assertTrue(review.getReported());
    }

    @Test
    @Order(8)
    @DisplayName("管理员忽略举报成功")
    void testIgnoreReviewReport() {
        BookReviewReportPageQuery queryDto = new BookReviewReportPageQuery();
        queryDto.setStatus(0);
        queryDto.setCurrent(0);
        queryDto.setSize(10);
        ApiResponse<List<BookReviewReportView>> queryResult = bookReviewReportService.query(queryDto);
        assertEquals(200, queryResult.getCode());
        Integer reportId = queryResult.getData().stream()
                .filter(item -> reviewId.equals(item.getReviewId()))
                .findFirst()
                .orElseThrow()
                .getId();

        var admin = createTestUser("review_admin_01", "审核管理员一", "review_admin_01@test.com");
        setCurrentUser(admin.getId(), UserRole.ADMIN.code());
        ApiResponse<Void> result = bookReviewReportService.ignore(reportId);
        assertEquals(200, result.getCode());

        queryDto.setStatus(2);
        ApiResponse<List<BookReviewReportView>> ignoredResult = bookReviewReportService.query(queryDto);
        assertTrue(ignoredResult.getData().stream().anyMatch(item -> reportId.equals(item.getId())));
        assertTrue(operationLogMapper.selectList(null).stream().anyMatch(log ->
                "审核".equals(log.getOperation())
                        && "书评举报".equals(log.getTarget())
                        && log.getDetail().contains("举报ID=" + reportId)
                        && log.getDetail().contains("处理结果=忽略举报")));
    }

    @Test
    @Order(9)
    @DisplayName("管理员隐藏被举报书评成功")
    void testHideReportedReview() {
        var reporter = createTestUser("reporter02", "第二举报人", "reporter2@test.com");
        setCurrentUser(reporter.getId(), reporter.getUserRole());
        ApiResponse<Void> reportResult = bookReviewService.report(reviewId, "需要管理员隐藏");
        assertEquals(200, reportResult.getCode());

        BookReviewReportPageQuery reportQuery = new BookReviewReportPageQuery();
        reportQuery.setStatus(0);
        reportQuery.setCurrent(0);
        reportQuery.setSize(10);
        ApiResponse<List<BookReviewReportView>> queryResult = bookReviewReportService.query(reportQuery);
        Integer reportId = queryResult.getData().stream()
                .filter(item -> reviewId.equals(item.getReviewId()))
                .findFirst()
                .orElseThrow()
                .getId();

        var admin = createTestUser("review_admin_02", "审核管理员二", "review_admin_02@test.com");
        setCurrentUser(admin.getId(), UserRole.ADMIN.code());
        ApiResponse<Void> hideResult = bookReviewReportService.hideReview(reportId);
        assertEquals(200, hideResult.getCode());

        BookReviewPageQuery reviewQuery = new BookReviewPageQuery();
        reviewQuery.setBookId(bookId);
        reviewQuery.setCurrent(0);
        reviewQuery.setSize(10);
        ApiResponse<List<BookReviewView>> afterHide = bookReviewService.query(reviewQuery);
        assertTrue(afterHide.getData().stream().noneMatch(item -> reviewId.equals(item.getId())));
        assertTrue(operationLogMapper.selectList(null).stream().anyMatch(log ->
                "审核".equals(log.getOperation())
                        && "书评举报".equals(log.getTarget())
                        && log.getDetail().contains("举报ID=" + reportId)
                        && log.getDetail().contains("处理结果=隐藏书评")));

        setCurrentUser(userId, UserRole.READER.code());
        BookReviewUpdateDto ownerUpdate = BookReviewUpdateDto.builder()
                .id(reviewId)
                .rating(5)
                .content("隐藏后修改内容不应改变审核状态")
                .build();
        assertEquals(200, bookReviewService.update(ownerUpdate).getCode());
        assertEquals(1, bookReviewMapper.getById(reviewId).getStatus());
    }

    @Test
    @Order(10)
    @DisplayName("非管理员删除他人评价被拒绝")
    void testBatchDeleteOtherUserReview() {
        var otherUser = createTestUser("other001", "其他人", "other@test.com");
        setCurrentUser(otherUser.getId(), otherUser.getUserRole());

        ApiResponse<Void> result = bookReviewService.batchDelete(Arrays.asList(reviewId));
        assertEquals(400, result.getCode());
    }

    @Test
    @Order(11)
    @DisplayName("管理员删除评价及关联回复并写入审计日志")
    void testAdminDeleteReviewIsAudited() {
        var admin = createTestUser("review_admin_03", "审核管理员三", "review_admin_03@test.com");
        setCurrentUser(admin.getId(), UserRole.ADMIN.code());

        ApiResponse<Void> result = bookReviewService.batchDelete(Arrays.asList(reviewId));
        assertEquals(200, result.getCode());
        OperationLog deleteLog = operationLogMapper.selectList(null).stream()
                .filter(log -> "删除".equals(log.getOperation())
                        && "书评及回复".equals(log.getTarget())
                        && log.getDetail().contains("书评ID=[" + reviewId + "]"))
                .findFirst()
                .orElseThrow();
        assertTrue(deleteLog.getDetail().contains("删除关联回复数=1"));
    }

    @Test
    @Order(12)
    @DisplayName("已下架图书不能新增书评")
    void testReviewDeletedBookRejected() {
        var user = createTestUser("reviewer_deleted", "下架书评读者", "reviewer_deleted@test.com");
        var book = createTestBook("已下架书评图书", "下架作者", 1);
        bookMapper.softDelete(List.of(book.getId()));
        setCurrentUser(user.getId(), user.getUserRole());

        ApiResponse<Void> result = bookReviewService.save(BookReviewCreateDto.builder()
                .bookId(book.getId())
                .rating(5)
                .content("不应写入")
                .build());

        assertEquals(400, result.getCode());
    }

    @Test
    @Order(13)
    @DisplayName("已禁言读者不能新增书评")
    void testMutedReaderCannotCreateReview() {
        var user = createTestUser("reviewer_muted", "禁言书评读者", "reviewer_muted@test.com");
        var book = createTestBook("禁言测试图书", "测试作者", 1);
        userMapper.update(org.darkroomlibrary.domain.model.User.builder()
                .id(user.getId())
                .isWord(true)
                .build());
        setCurrentUser(user.getId(), user.getUserRole());

        ApiResponse<Void> result = bookReviewService.save(BookReviewCreateDto.builder()
                .bookId(book.getId())
                .rating(5)
                .content("这条书评不应被保存")
                .build());

        assertEquals(400, result.getCode());
        assertEquals("当前账号已被禁言，暂不能发布或修改内容", result.getMsg());
    }

    @Test
    @Order(14)
    @DisplayName("已隐藏书评不能继续点赞回复或举报")
    void testHiddenReviewRejectsInteractions() {
        var owner = createTestUser("hidden_owner", "隐藏书评作者", "hidden_owner@test.com");
        var visitor = createTestUser("hidden_visitor", "隐藏书评访客", "hidden_visitor@test.com");
        var book = createTestBook("隐藏互动测试图书", "测试作者", 1);
        org.darkroomlibrary.domain.model.BookReview review =
                org.darkroomlibrary.domain.model.BookReview.builder()
                        .userId(owner.getId())
                        .bookId(book.getId())
                        .rating(5)
                        .content("已隐藏内容")
                        .status(1)
                        .createTime(java.time.LocalDateTime.now())
                        .build();
        bookReviewMapper.insert(review);
        setCurrentUser(visitor.getId(), visitor.getUserRole());

        assertEquals(400, bookReviewService.toggleLike(review.getId()).getCode());
        assertEquals(400, bookReviewService.reply(
                review.getId(), "不应写入的回复", owner.getId()).getCode());
        assertEquals(400, bookReviewService.report(
                review.getId(), "不应写入的举报").getCode());
    }
}
