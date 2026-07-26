package org.darkroomlibrary.web.dto.query;

import org.darkroomlibrary.web.dto.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 图书评价查询DTO
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class BookReviewPageQuery extends PageQuery {
    /**
     * 用户ID
     */
    private Integer userId;
    /**
     * 图书ID
     */
    private Integer bookId;
    /**
     * 排序：latest=最新，hot=最热
     */
    private String sortBy;
}
