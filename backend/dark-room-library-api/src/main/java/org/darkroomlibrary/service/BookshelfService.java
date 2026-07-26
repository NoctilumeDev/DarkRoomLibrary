package org.darkroomlibrary.service;

import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.BookshelfPageQuery;
import org.darkroomlibrary.domain.model.Bookshelf;

import java.util.List;

/**
 * 书架服务接口
 */
public interface BookshelfService {

    ApiResponse<Void> save(Bookshelf bookshelf);

    ApiResponse<Void> update(Bookshelf bookshelf);

    ApiResponse<Void> batchDelete(List<Integer> ids);

    ApiResponse<List<Bookshelf>> query(BookshelfPageQuery dto);

    ApiResponse<List<Bookshelf>> queryAll();
}
