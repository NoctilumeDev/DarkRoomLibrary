package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.mapper.BookMapper;
import org.darkroomlibrary.mapper.BookReviewLikeMapper;
import org.darkroomlibrary.mapper.BookReviewMapper;
import org.darkroomlibrary.mapper.BookReviewReplyMapper;
import org.darkroomlibrary.mapper.BookReviewReportMapper;
import org.darkroomlibrary.mapper.UserMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.response.PageResponse;
import org.darkroomlibrary.web.dto.query.BookReviewPageQuery;
import org.darkroomlibrary.web.dto.command.BookReviewCreateDto;
import org.darkroomlibrary.web.dto.command.BookReviewUpdateDto;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.domain.model.BookReview;
import org.darkroomlibrary.domain.model.BookReviewLike;
import org.darkroomlibrary.domain.model.BookReviewReply;
import org.darkroomlibrary.domain.model.BookReviewReport;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.web.view.BookReviewReplyView;
import org.darkroomlibrary.web.view.BookReviewView;
import org.darkroomlibrary.web.view.InteractionSummary;
import org.darkroomlibrary.service.ContentPostingPolicy;
import org.darkroomlibrary.service.OperationAuditService;
import org.darkroomlibrary.service.BookReviewService;
import org.darkroomlibrary.utils.ContentSanitizer;
import org.darkroomlibrary.utils.IdListUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 图书评价服务实现
 */
@Service
public class BookReviewServiceImpl implements BookReviewService {

    @Resource
    private BookMapper bookMapper;
    @Resource
    private BookReviewMapper bookReviewMapper;
    @Resource
    private BookReviewLikeMapper bookReviewLikeMapper;
    @Resource
    private BookReviewReplyMapper bookReviewReplyMapper;
    @Resource
    private BookReviewReportMapper bookReviewReportMapper;
    @Resource
    private OperationAuditService operationAuditService;
    @Resource
    private ContentPostingPolicy contentPostingPolicy;
    @Resource
    private UserMapper userMapper;

    @Override
    @Transactional
    public ApiResponse<Void> save(BookReviewCreateDto dto) {
        if (dto == null) {
            return ApiResponse.error("评价参数不能为空");
        }
        String postingError = contentPostingPolicy.currentUserRejectionReason();
        if (postingError != null) {
            return ApiResponse.error(postingError);
        }
        Book book = dto.getBookId() == null ? null : bookMapper.findByIdForUpdate(dto.getBookId());
        if (book == null || Boolean.TRUE.equals(book.getIsDeleted())) {
            return ApiResponse.error("图书不存在或已下架");
        }
        Integer userId = CurrentUserContext.userId();
        if (dto.getRating() == null || dto.getRating() < 1 || dto.getRating() > 5) {
            return ApiResponse.error("评分必须在1-5之间");
        }
        if (ContentSanitizer.exceedsLength(
                dto.getContent(), ContentSanitizer.REVIEW_MAX_LENGTH)) {
            return ApiResponse.error("评价内容不能超过1000个字符");
        }
        String cleanContent = ContentSanitizer.plainText(dto.getContent());
        if (cleanContent == null || cleanContent.isEmpty()) {
            return ApiResponse.error("评价内容不能为空");
        }
        BookReview bookReview = BookReview.builder()
                .userId(userId)
                .bookId(dto.getBookId())
                .rating(dto.getRating())
                .content(cleanContent)
                .status(0)
                .createTime(LocalDateTime.now())
                .build();
        if (bookReviewMapper.insert(bookReview) != 1) {
            return ApiResponse.error("评价发布失败，请重试");
        }
        return ApiResponse.success("评价成功");
    }

    @Override
    @Transactional
    public ApiResponse<Void> update(BookReviewUpdateDto dto) {
        if (dto == null || dto.getId() == null) {
            return ApiResponse.error("评价参数不能为空");
        }
        String postingError = contentPostingPolicy.currentUserRejectionReason();
        if (postingError != null) {
            return ApiResponse.error(postingError);
        }
        BookReview exist = bookReviewMapper.findByIdForUpdate(dto.getId());
        if (exist == null) {
            return ApiResponse.error("评价不存在");
        }
        Integer userId = CurrentUserContext.userId();
        if (!exist.getUserId().equals(userId)) {
            return ApiResponse.error("只能修改自己的评价");
        }
        if (dto.getRating() != null
                && (dto.getRating() < 1 || dto.getRating() > 5)) {
            return ApiResponse.error("评分必须在1-5之间");
        }
        String cleanContent = null;
        if (dto.getContent() != null) {
            if (ContentSanitizer.exceedsLength(
                    dto.getContent(), ContentSanitizer.REVIEW_MAX_LENGTH)) {
                return ApiResponse.error("评价内容不能超过1000个字符");
            }
            cleanContent = ContentSanitizer.plainText(dto.getContent());
            if (cleanContent == null || cleanContent.isEmpty()) {
                return ApiResponse.error("评价内容不能为空");
            }
        }
        BookReview update = BookReview.builder()
                .id(dto.getId())
                .rating(dto.getRating())
                .content(cleanContent)
                .build();
        if (bookReviewMapper.update(update) == 0) {
            return ApiResponse.error("评价状态已变化，请刷新后重试");
        }
        return ApiResponse.success("修改评价成功");
    }

    @Override
    @Transactional
    public ApiResponse<Void> batchDelete(List<Integer> ids) {
        List<Integer> normalizedIds = IdListUtils.normalize(ids);
        if (normalizedIds.isEmpty()) {
            return ApiResponse.error("请选择要删除的评价");
        }
        if (IdListUtils.exceedsBatchLimit(normalizedIds)) {
            return ApiResponse.error("单次最多删除" + IdListUtils.MAX_BATCH_SIZE + "条评价");
        }
        List<BookReview> reviews = bookReviewMapper.findByIdsForUpdate(normalizedIds);
        if (reviews.size() != normalizedIds.size()) {
            return ApiResponse.error("部分评价不存在");
        }
        // 非管理员只能删除自己的评价
        if (!CurrentUserContext.isAdministrator()) {
            Integer currentUserId = CurrentUserContext.userId();
            for (BookReview review : reviews) {
                if (!Objects.equals(review.getUserId(), currentUserId)) {
                    return ApiResponse.error("只能删除自己的评价");
                }
            }
        }
        boolean adminOperation = CurrentUserContext.isAdministrator();
        bookReviewLikeMapper.deleteByReviewIds(normalizedIds);
        int deletedReplyCount = bookReviewReplyMapper.deleteByReviewIds(normalizedIds);
        bookReviewReportMapper.deleteByReviewIds(normalizedIds);
        if (bookReviewMapper.batchDelete(normalizedIds) != normalizedIds.size()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error("评价状态已变化，请刷新后重试");
        }
        if (adminOperation) {
            operationAuditService.record("删除", "书评及回复",
                    "书评ID=" + normalizedIds + "，删除书评数=" + normalizedIds.size()
                            + "，删除关联回复数=" + deletedReplyCount
                            + "，关联点赞和举报记录已清理");
        }
        return ApiResponse.success("删除评价成功");
    }

    @Override
    public ApiResponse<List<BookReviewView>> query(BookReviewPageQuery dto) {
        List<BookReviewView> list = bookReviewMapper.query(dto);
        fillInteractionInfo(list);
        Integer total = bookReviewMapper.queryCount(dto);
        return PageResponse.success(list, total);
    }

    @Override
    @Transactional
    public ApiResponse<Boolean> toggleLike(Integer reviewId) {
        String accountError = contentPostingPolicy.currentUserAccountRejectionReason();
        if (accountError != null) {
            return ApiResponse.error(accountError);
        }
        BookReview review = reviewId == null ? null : bookReviewMapper.findByIdForUpdate(reviewId);
        if (review == null || !Objects.equals(review.getStatus(), 0)) {
            return ApiResponse.error("评价不存在或已隐藏");
        }
        Integer userId = CurrentUserContext.userId();
        Integer exists = bookReviewLikeMapper.countByReviewIdAndUserId(reviewId, userId);
        if (exists != null && exists > 0) {
            if (bookReviewLikeMapper.deleteByReviewIdAndUserId(reviewId, userId) != 1) {
                return ApiResponse.error("点赞状态已变化，请刷新后重试");
            }
            return ApiResponse.success("已取消点赞", false);
        }
        BookReviewLike like = BookReviewLike.builder()
                .reviewId(reviewId)
                .userId(userId)
                .createTime(LocalDateTime.now())
                .build();
        try {
            if (bookReviewLikeMapper.insert(like) != 1) {
                return ApiResponse.error("点赞失败，请重试");
            }
        } catch (DuplicateKeyException e) {
            return ApiResponse.success("已点赞", true);
        }
        return ApiResponse.success("点赞成功", true);
    }

    @Override
    @Transactional
    public ApiResponse<Void> reply(Integer reviewId, String content, Integer replyToUserId) {
        BookReview snapshot = reviewId == null ? null : bookReviewMapper.getById(reviewId);
        if (snapshot == null || !Objects.equals(snapshot.getStatus(), 0)) {
            return ApiResponse.error("评价不存在或已隐藏");
        }
        Map<Integer, User> lockedUsers =
                lockUsers(CurrentUserContext.userId(), snapshot.getUserId());
        User currentUser = lockedUsers.get(CurrentUserContext.userId());
        String postingError = contentPostingPolicy.postingRejectionReason(currentUser);
        if (postingError != null) {
            return ApiResponse.error(postingError);
        }
        if (lockedUsers.get(snapshot.getUserId()) == null) {
            return ApiResponse.error("书评作者不存在或账号已删除");
        }
        BookReview review = reviewId == null ? null : bookReviewMapper.findByIdForUpdate(reviewId);
        if (review == null
                || !Objects.equals(review.getStatus(), 0)
                || !Objects.equals(review.getUserId(), snapshot.getUserId())) {
            return ApiResponse.error("评价状态已变化，请刷新后重试");
        }
        if (ContentSanitizer.exceedsLength(content, ContentSanitizer.REVIEW_REPLY_MAX_LENGTH)) {
            return ApiResponse.error("回复内容不能超过500个字符");
        }
        String cleanContent = ContentSanitizer.plainText(content);
        if (cleanContent == null || cleanContent.isEmpty()) {
            return ApiResponse.error("回复内容不能为空");
        }
        if (replyToUserId != null && !Objects.equals(replyToUserId, review.getUserId())) {
            return ApiResponse.error("回复目标不属于当前书评");
        }
        BookReviewReply reply = BookReviewReply.builder()
                .reviewId(reviewId)
                .userId(CurrentUserContext.userId())
                .replyToUserId(review.getUserId())
                .content(cleanContent)
                .createTime(LocalDateTime.now())
                .build();
        if (bookReviewReplyMapper.insert(reply) != 1) {
            return ApiResponse.error("回复发布失败，请重试");
        }
        return ApiResponse.success("回复成功");
    }

    @Override
    @Transactional
    public ApiResponse<Void> report(Integer reviewId, String reason) {
        String accountError = contentPostingPolicy.currentUserAccountRejectionReason();
        if (accountError != null) {
            return ApiResponse.error(accountError);
        }
        BookReview review = reviewId == null ? null : bookReviewMapper.findByIdForUpdate(reviewId);
        if (review == null || !Objects.equals(review.getStatus(), 0)) {
            return ApiResponse.error("评价不存在或已隐藏");
        }
        Integer userId = CurrentUserContext.userId();
        if (Objects.equals(review.getUserId(), userId)) {
            return ApiResponse.error("不能举报自己的评价");
        }
        String cleanReason = ContentSanitizer.plainText(reason);
        cleanReason = cleanReason == null ? "" : cleanReason;
        if (cleanReason.isEmpty()) {
            return ApiResponse.error("举报原因不能为空");
        }
        if (cleanReason.length() > 200) {
            return ApiResponse.error("举报原因不能超过200字");
        }
        Integer exists = bookReviewReportMapper.countByReviewIdAndUserId(reviewId, userId);
        if (exists != null && exists > 0) {
            return ApiResponse.error("你已举报过这条书评");
        }
        BookReviewReport report = BookReviewReport.builder()
                .reviewId(reviewId)
                .userId(userId)
                .reason(cleanReason)
                .status(0)
                .createTime(LocalDateTime.now())
                .build();
        try {
            if (bookReviewReportMapper.insert(report) != 1) {
                return ApiResponse.error("举报提交失败，请重试");
            }
        } catch (DuplicateKeyException e) {
            return ApiResponse.error("你已举报过这条书评");
        }
        return ApiResponse.success("举报已提交，等待管理员审核");
    }

    private Map<Integer, User> lockUsers(Integer... userIds) {
        List<Integer> ids = java.util.Arrays.stream(userIds)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        List<User> users = ids.isEmpty() ? List.of() : userMapper.findByIdsForUpdate(ids);
        Map<Integer, User> locked = new LinkedHashMap<>();
        for (Integer id : ids) {
            locked.put(id, null);
        }
        for (User user : users) {
            locked.put(user.getId(), user);
        }
        return locked;
    }

    private void fillInteractionInfo(List<BookReviewView> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Integer currentUserId = CurrentUserContext.userId();
        List<Integer> reviewIds = list.stream()
                .map(BookReviewView::getId)
                .collect(Collectors.toList());
        List<BookReviewReplyView> replies = bookReviewReplyMapper.queryByReviewIds(reviewIds);
        Map<Integer, List<BookReviewReplyView>> replyMap = replies == null
                ? Collections.emptyMap()
                : replies.stream().collect(Collectors.groupingBy(BookReviewReplyView::getReviewId));
        Map<Integer, InteractionSummary> likeSummary = summariesByReviewId(
                bookReviewLikeMapper.summarizeByReviewIds(reviewIds, currentUserId));
        Map<Integer, InteractionSummary> reportSummary = summariesByReviewId(
                bookReviewReportMapper.summarizeByReviewIds(reviewIds, currentUserId));
        for (BookReviewView review : list) {
            review.setContent(ContentSanitizer.plainText(review.getContent()));
            InteractionSummary likes = likeSummary.get(review.getId());
            InteractionSummary reports = reportSummary.get(review.getId());
            review.setLikeCount(summaryCount(likes));
            review.setReportCount(summaryCount(reports));
            review.setLiked(currentUserId != null && viewerInteracted(likes));
            review.setReported(currentUserId != null && viewerInteracted(reports));
            List<BookReviewReplyView> reviewReplies =
                    replyMap.getOrDefault(review.getId(), Collections.emptyList());
            for (BookReviewReplyView reply : reviewReplies) {
                reply.setContent(ContentSanitizer.plainText(reply.getContent()));
            }
            review.setReplies(reviewReplies);
        }
    }

    private Map<Integer, InteractionSummary> summariesByReviewId(List<InteractionSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return Collections.emptyMap();
        }
        return summaries.stream().collect(Collectors.toMap(
                InteractionSummary::getReviewId,
                summary -> summary,
                (left, right) -> left,
                LinkedHashMap::new));
    }

    private int summaryCount(InteractionSummary summary) {
        return summary == null || summary.getTotalCount() == null ? 0 : summary.getTotalCount();
    }

    private boolean viewerInteracted(InteractionSummary summary) {
        return summary != null
                && summary.getViewerCount() != null
                && summary.getViewerCount() > 0;
    }
}
