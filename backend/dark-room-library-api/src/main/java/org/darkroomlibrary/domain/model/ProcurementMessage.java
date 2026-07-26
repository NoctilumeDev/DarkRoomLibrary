package org.darkroomlibrary.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 采购协作消息实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("procurement_message")
public class ProcurementMessage {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer orderId;

    /**
     * 0=管理员与采购员，1=采购员与物流员
     */
    private Integer channelType;

    private Integer senderId;

    private Integer receiverId;

    private String content;

    private Boolean readStatus;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
