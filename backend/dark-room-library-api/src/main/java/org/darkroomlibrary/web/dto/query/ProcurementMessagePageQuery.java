package org.darkroomlibrary.web.dto.query;

import org.darkroomlibrary.web.dto.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 采购协作消息查询DTO
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ProcurementMessagePageQuery extends PageQuery {

    private Integer orderId;

    private Integer channelType;

    private Boolean unreadOnly;
}
