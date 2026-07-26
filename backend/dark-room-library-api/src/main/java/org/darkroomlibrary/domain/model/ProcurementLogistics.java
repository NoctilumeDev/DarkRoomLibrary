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
 * 采购物流进度实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("procurement_logistics")
public class ProcurementLogistics {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer orderId;

    private Integer logisticsUserId;

    /**
     * 0=待接收，1=运输中，2=已到馆，3=已入库
     */
    private Integer status;

    private String trackingNo;

    private String carrier;

    private String remark;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
