package org.darkroomlibrary.web.view;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 采购协作消息视图
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProcurementMessageView {

    private Integer id;
    private Integer orderId;
    private Integer channelType;
    private Integer senderId;
    private String senderName;
    private Integer senderRole;
    private Integer receiverId;
    private String receiverName;
    private Integer receiverRole;
    private String content;
    private Boolean readStatus;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
