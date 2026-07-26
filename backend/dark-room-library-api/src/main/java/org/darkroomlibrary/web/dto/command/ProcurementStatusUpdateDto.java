package org.darkroomlibrary.web.dto.command;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 采购状态更新DTO
 */
@Data
public class ProcurementStatusUpdateDto {

    @NotNull(message = "采购单ID不能为空")
    private Integer id;

    @NotNull(message = "采购状态不能为空")
    private Integer status;

    @Size(max = 1000, message = "采购备注不能超过1000个字符")
    private String purchaseNote;
}
