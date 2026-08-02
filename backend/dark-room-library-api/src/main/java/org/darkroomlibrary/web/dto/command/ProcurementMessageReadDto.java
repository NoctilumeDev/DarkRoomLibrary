package org.darkroomlibrary.web.dto.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Marks only messages that were actually rendered to the current receiver.
 */
@Data
public class ProcurementMessageReadDto {

    @NotNull(message = "采购单不能为空")
    @Positive(message = "采购单不正确")
    private Integer orderId;

    @NotNull(message = "消息通道不能为空")
    @Min(value = 0, message = "消息通道不正确")
    @Max(value = 1, message = "消息通道不正确")
    private Integer channelType;

    @NotEmpty(message = "请选择已展示消息")
    @Size(max = 100, message = "单次最多确认100条消息")
    private List<@NotNull @Positive Integer> messageIds;
}
