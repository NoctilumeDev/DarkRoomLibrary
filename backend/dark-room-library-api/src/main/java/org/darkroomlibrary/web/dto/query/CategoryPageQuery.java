package org.darkroomlibrary.web.dto.query;

import org.darkroomlibrary.web.dto.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分类查询DTO
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class CategoryPageQuery extends PageQuery {
    /**
     * 分类名称
     */
    private String name;
}