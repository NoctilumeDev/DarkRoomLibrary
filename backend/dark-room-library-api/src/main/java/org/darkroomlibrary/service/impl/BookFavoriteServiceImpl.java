package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.mapper.BookMapper;
import org.darkroomlibrary.mapper.BookFavoriteMapper;
import org.darkroomlibrary.mapper.UserMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.response.PageResponse;
import org.darkroomlibrary.web.dto.query.BookFavoritePageQuery;
import org.darkroomlibrary.domain.type.AccountStatus;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.domain.model.BookFavorite;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.web.view.BookFavoriteView;
import org.darkroomlibrary.service.BookFavoriteService;
import org.darkroomlibrary.service.RecommendationService;
import org.darkroomlibrary.service.support.RecommendationSourceVersionService;
import org.darkroomlibrary.utils.TransactionCallbacks;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 图书收藏服务实现
 */
@Service
public class BookFavoriteServiceImpl implements BookFavoriteService {

    @Resource
    private BookFavoriteMapper bookFavoriteMapper;

    @Resource
    private BookMapper bookMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RecommendationService recommendationService;

    @Resource
    private RecommendationSourceVersionService recommendationSourceVersionService;

    @Override
    @Transactional
    public ApiResponse<Void> addFavorite(Integer bookId) {
        Integer userId = CurrentUserContext.userId();
        if (userId == null) {
            return ApiResponse.error("身份认证失败，请先登录");
        }
        User user = userMapper.findByIdForUpdate(userId);
        if (user == null
                || !Objects.equals(user.getAccountStatus(), AccountStatus.NORMAL.code())
                || Boolean.TRUE.equals(user.getIsLogin())
                || !Objects.equals(user.getUserRole(), CurrentUserContext.roleCode())
                || !Objects.equals(user.getUserRole(), UserRole.READER.code())) {
            return ApiResponse.error("当前账号状态不允许收藏图书");
        }
        Book book = bookId == null ? null : bookMapper.findByIdForUpdate(bookId);
        if (book == null || Boolean.TRUE.equals(book.getIsDeleted())) {
            return ApiResponse.error("图书不存在或已下架");
        }
        Integer count = bookFavoriteMapper.isFavorited(userId, bookId);
        if (count != null && count > 0) {
            return ApiResponse.error("已收藏该图书");
        }
        BookFavorite favorite = BookFavorite.builder()
                .userId(userId)
                .bookId(bookId)
                .createTime(LocalDateTime.now())
                .build();
        try {
            if (bookFavoriteMapper.insert(favorite) != 1) {
                return ApiResponse.error("收藏失败，请重试");
            }
        } catch (DuplicateKeyException e) {
            return ApiResponse.error("已收藏该图书");
        }
        recommendationSourceVersionService.invalidateUserAndGlobalAfterCommit(userId);
        TransactionCallbacks.afterCommit(() -> recommendationService.attributeFavorite(userId, bookId));
        return ApiResponse.success("收藏成功");
    }

    @Override
    @Transactional
    public ApiResponse<Void> removeFavorite(Integer bookId) {
        Integer userId = CurrentUserContext.userId();
        if (bookFavoriteMapper.removeByUserAndBook(userId, bookId) > 0) {
            recommendationSourceVersionService.invalidateUserAndGlobalAfterCommit(userId);
        }
        return ApiResponse.success("取消收藏成功");
    }

    @Override
    public ApiResponse<Boolean> isFavorited(Integer bookId) {
        Integer userId = CurrentUserContext.userId();
        Integer count = bookFavoriteMapper.isFavorited(userId, bookId);
        return ApiResponse.success(count != null && count > 0);
    }

    @Override
    public ApiResponse<List<BookFavoriteView>> query(BookFavoritePageQuery dto) {
        if (dto == null) {
            dto = new BookFavoritePageQuery();
        }
        dto.setUserId(CurrentUserContext.userId());
        List<BookFavoriteView> list = bookFavoriteMapper.query(dto);
        Integer total = bookFavoriteMapper.queryCount(dto);
        return PageResponse.success(list, total);
    }
}
