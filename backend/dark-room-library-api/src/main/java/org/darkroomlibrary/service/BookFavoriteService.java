package org.darkroomlibrary.service;

import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.BookFavoritePageQuery;
import org.darkroomlibrary.web.view.BookFavoriteView;

import java.util.List;

/**
 * 图书收藏服务接口
 */
public interface BookFavoriteService {

    ApiResponse<Void> addFavorite(Integer bookId);

    ApiResponse<Void> removeFavorite(Integer bookId);

    ApiResponse<Boolean> isFavorited(Integer bookId);

    ApiResponse<List<BookFavoriteView>> query(BookFavoritePageQuery dto);
}