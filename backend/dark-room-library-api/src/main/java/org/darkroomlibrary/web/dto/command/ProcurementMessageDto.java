package org.darkroomlibrary.web.dto.command;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 采购协作消息DTO
 */
@Data
public class ProcurementMessageDto {

    @NotNull(message = "采购单ID不能为空")
    private Integer orderId;

    @NotNull(message = "消息通道不能为空")
    private Integer channelType;

    @NotNull(message = "接收人不能为空")
    private Integer receiverId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 1000, message = "消息内容不能超过1000个字符")
    private String content;
}
