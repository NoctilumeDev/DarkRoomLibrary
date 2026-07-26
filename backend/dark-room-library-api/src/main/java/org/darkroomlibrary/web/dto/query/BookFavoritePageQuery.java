package org.darkroomlibrary.web.dto.query;

import org.darkroomlibrary.web.dto.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 图书收藏查询DTO
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class BookFavoritePageQuery extends PageQuery {
    /**
     * 用户ID
     */
    private Integer userId;
    /**
     * 图书ID
     */
    private Integer bookId;
}