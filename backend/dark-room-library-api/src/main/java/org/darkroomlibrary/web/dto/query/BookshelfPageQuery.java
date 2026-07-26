package org.darkroomlibrary.web.dto.query;

import org.darkroomlibrary.web.dto.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 书架查询DTO
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class BookshelfPageQuery extends PageQuery {
    /**
     * 书架名称
     */
    private String name;
    /**
     * 所在位置
     */
    private String location;
}
