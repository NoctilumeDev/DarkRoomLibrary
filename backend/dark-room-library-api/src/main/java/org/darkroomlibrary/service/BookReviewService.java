package org.darkroomlibrary.service;

import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.BookReviewPageQuery;
import org.darkroomlibrary.web.dto.command.BookReviewCreateDto;
import org.darkroomlibrary.web.dto.command.BookReviewUpdateDto;
import org.darkroomlibrary.web.view.BookReviewView;

import java.util.List;

/**
 * 图书评价服务接口
 */
public interface BookReviewService {

    ApiResponse<Void> save(BookReviewCreateDto dto);

    ApiResponse<Void> update(BookReviewUpdateDto dto);

    ApiResponse<Void> batchDelete(List<Integer> ids);

    ApiResponse<List<BookReviewView>> query(BookReviewPageQuery dto);

    ApiResponse<Boolean> toggleLike(Integer reviewId);

    ApiResponse<Void> reply(Integer reviewId, String content, Integer replyToUserId);

    ApiResponse<Void> report(Integer reviewId, String reason);
}
