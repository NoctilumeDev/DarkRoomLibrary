package org.darkroomlibrary.service;

import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.BookPageQuery;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.web.view.MetricPoint;

import java.util.List;

/**
 * 图书服务接口
 */
public interface BookService {

    /**
     * 新增图书
     *
     * @param book 图书信息
     * @return ApiResponse<Void>
     */
    ApiResponse<Void> save(Book book);

    /**
     * 修改图书
     *
     * @param book 图书信息
     * @return ApiResponse<Void>
     */
    ApiResponse<Void> update(Book book);

    /**
     * 批量删除图书
     *
     * @param ids 图书ID集合
     * @return ApiResponse<Void>
     */
    ApiResponse<Void> batchDelete(List<Integer> ids);

    /**
     * 分页查询图书
     *
     * @param dto 查询参数
     * @return ApiResponse<List<Book>>
     */
    ApiResponse<List<Book>> query(BookPageQuery dto);

    /**
     * 统计近N天新增图书
     */
    ApiResponse<List<MetricPoint>> queryByDays(Integer day);

    /**
     * 恢复已删除的图书
     */
    ApiResponse<Void> restore(List<Integer> ids);
}
