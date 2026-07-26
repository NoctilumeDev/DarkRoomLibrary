package org.darkroomlibrary.web.dto.query;

import org.darkroomlibrary.web.dto.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 图书查询DTO
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class BookPageQuery extends PageQuery {
    /**
     * 图书名称
     */
    private String name;
    /**
     * 作者
     */
    private String author;
    /**
     * 分类
     */
    private String category;

    /** 是否查询已删除图书；不传时默认只查询正常图书。 */
    private Boolean deleted;
}
