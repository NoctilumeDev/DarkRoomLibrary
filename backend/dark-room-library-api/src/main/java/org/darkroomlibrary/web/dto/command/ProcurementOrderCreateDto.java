package org.darkroomlibrary.web.dto.command;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建采购单DTO
 */
@Data
public class ProcurementOrderCreateDto {

    @NotNull(message = "图书ID不能为空")
    private Integer bookId;

    @NotNull(message = "采购数量不能为空")
    @Min(value = 1, message = "采购数量必须大于0")
    private Integer requestCount;

    private Integer purchaserId;

    @Size(max = 1000, message = "申请说明不能超过1000个字符")
    private String requestNote;
}
