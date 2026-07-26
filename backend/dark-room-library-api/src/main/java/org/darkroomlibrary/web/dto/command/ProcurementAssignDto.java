package org.darkroomlibrary.web.dto.command;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

/**
 * 采购角色分配DTO
 */
@Data
public class ProcurementAssignDto {

    @NotNull(message = "采购单ID不能为空")
    private Integer orderId;

    @NotNull(message = "协作人员ID不能为空")
    private Integer userId;
}
