package org.darkroomlibrary.web.dto.command;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 物流进度更新DTO
 */
@Data
public class ProcurementLogisticsUpdateDto {

    @NotNull(message = "采购单ID不能为空")
    private Integer orderId;

    @NotNull(message = "物流状态不能为空")
    private Integer status;

    @Size(max = 100, message = "运单号不能超过100个字符")
    private String trackingNo;

    @Size(max = 100, message = "承运方不能超过100个字符")
    private String carrier;

    @Size(max = 1000, message = "物流备注不能超过1000个字符")
    private String remark;
}
