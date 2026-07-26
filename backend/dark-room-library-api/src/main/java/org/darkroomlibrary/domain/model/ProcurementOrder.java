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
 * 采购单实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("procurement_order")
public class ProcurementOrder {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer bookId;

    private String bookName;

    private String isbn;

    private String category;

    private Integer requestCount;

    /**
     * 0=待采购，1=采购中，2=已下单，3=已发货，4=已到货，5=已入库，6=已完成，7=已取消
     */
    private Integer status;

    private Integer requesterId;

    private Integer purchaserId;

    private Integer logisticsId;

    private String requestNote;

    private String purchaseNote;

    private Boolean stockApplied;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime orderTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime shippedTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime arrivalTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedTime;
}
