package org.darkroomlibrary.web.dto.query;

import org.darkroomlibrary.web.dto.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 采购单查询DTO
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ProcurementOrderPageQuery extends PageQuery {

    private Integer bookId;

    private String bookName;

    private Integer status;

    private Integer requesterId;

    private Integer purchaserId;

    private Integer logisticsId;

    /**
     * 采购员列表页使用：查看未指派和自己负责的采购单。
     */
    private Boolean includeUnassignedForPurchaser;
}
