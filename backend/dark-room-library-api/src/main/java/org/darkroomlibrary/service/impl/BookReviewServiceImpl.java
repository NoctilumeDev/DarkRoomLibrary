package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.mapper.BookMapper;
import org.darkroomlibrary.mapper.BookReviewLikeMapper;
import org.darkroomlibrary.mapper.BookReviewMapper;
import org.darkroomlibrary.mapper.BookReviewReplyMapper;
import org.darkroomlibrary.mapper.BookReviewReportMapper;
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
import org.darkroomlibrary.web.view.BookReviewReplyView;
import org.darkroomlibrary.web.view.BookReviewView;
import org.darkroomlibrary.service.ContentPostingPolicy;
import org.darkroomlibrary.service.OperationAuditService;
import org.darkroomlibrary.service.BookReviewService;
import org.darkroomlibrary.utils.ContentSanitizer;
import org.darkroomlibrary.utils.IdListUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
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
        Book book = bookMapper.getById(dto.getBookId());
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
        bookReviewMapper.insert(bookReview);
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
        BookReview exist = bookReviewMapper.getById(dto.getId());
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
        bookReviewMapper.update(update);
        return ApiResponse.success("修改评价成功");
    }

    @Override
    @Transactional
    public ApiResponse<Void> batchDelete(List<Integer> ids) {
        List<Integer> normalizedIds = IdListUtils.normalize(ids);
        if (normalizedIds.isEmpty()) {
            return ApiResponse.error("请选择要删除的评价");
        }
        // 非管理员只能删除自己的评价
        if (!CurrentUserContext.isAdministrator()) {
            Integer currentUserId = CurrentUserContext.userId();
            for (Integer id : normalizedIds) {
                BookReview review = bookReviewMapper.getById(id);
                if (review == null || !Objects.equals(review.getUserId(), currentUserId)) {
                    return ApiResponse.error("只能删除自己的评价");
                }
            }
        }
        boolean adminOperation = CurrentUserContext.isAdministrator();
        bookReviewLikeMapper.deleteByReviewIds(normalizedIds);
        int deletedReplyCount = bookReviewReplyMapper.deleteByReviewIds(normalizedIds);
        bookReviewReportMapper.deleteByReviewIds(normalizedIds);
        bookReviewMapper.batchDelete(normalizedIds);
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
        BookReview review = bookReviewMapper.getById(reviewId);
        if (review == null || !Objects.equals(review.getStatus(), 0)) {
            return ApiResponse.error("评价不存在或已隐藏");
        }
        Integer userId = CurrentUserContext.userId();
        Integer exists = bookReviewLikeMapper.countByReviewIdAndUserId(reviewId, userId);
        if (exists != null && exists > 0) {
            bookReviewLikeMapper.deleteByReviewIdAndUserId(reviewId, userId);
            return ApiResponse.success("已取消点赞", false);
        }
        BookReviewLike like = BookReviewLike.builder()
                .reviewId(reviewId)
                .userId(userId)
                .createTime(LocalDateTime.now())
                .build();
        try {
            bookReviewLikeMapper.insert(like);
        } catch (DuplicateKeyException e) {
            return ApiResponse.success("已点赞", true);
        }
        return ApiResponse.success("点赞成功", true);
    }

    @Override
    @Transactional
    public ApiResponse<Void> reply(Integer reviewId, String content, Integer replyToUserId) {
        String postingError = contentPostingPolicy.currentUserRejectionReason();
        if (postingError != null) {
            return ApiResponse.error(postingError);
        }
        BookReview review = bookReviewMapper.getById(reviewId);
        if (review == null || !Objects.equals(review.getStatus(), 0)) {
            return ApiResponse.error("评价不存在或已隐藏");
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
        bookReviewReplyMapper.insert(reply);
        return ApiResponse.success("回复成功");
    }

    @Override
    @Transactional
    public ApiResponse<Void> report(Integer reviewId, String reason) {
        BookReview review = bookReviewMapper.getById(reviewId);
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
            bookReviewReportMapper.insert(report);
        } catch (DuplicateKeyException e) {
            return ApiResponse.error("你已举报过这条书评");
        }
        return ApiResponse.success("举报已提交，等待管理员审核");
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
        for (BookReviewView review : list) {
            review.setContent(ContentSanitizer.plainText(review.getContent()));
            Integer likeCount = bookReviewLikeMapper.countByReviewId(review.getId());
            review.setLikeCount(likeCount == null ? 0 : likeCount);
            Integer reportCount = bookReviewReportMapper.countPendingByReviewId(review.getId());
            review.setReportCount(reportCount == null ? 0 : reportCount);
            if (currentUserId == null) {
                review.setLiked(false);
                review.setReported(false);
            } else {
                Integer liked = bookReviewLikeMapper.countByReviewIdAndUserId(review.getId(), currentUserId);
                review.setLiked(liked != null && liked > 0);
                Integer reported = bookReviewReportMapper.countByReviewIdAndUserId(review.getId(), currentUserId);
                review.setReported(reported != null && reported > 0);
            }
            List<BookReviewReplyView> reviewReplies =
                    replyMap.getOrDefault(review.getId(), Collections.emptyList());
            for (BookReviewReplyView reply : reviewReplies) {
                reply.setContent(ContentSanitizer.plainText(reply.getContent()));
            }
            review.setReplies(reviewReplies);
        }
    }
}
