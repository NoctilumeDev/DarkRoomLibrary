package org.darkroomlibrary.web.view;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 采购单视图
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProcurementOrderView {

    private Integer id;
    private Integer bookId;
    private String bookName;
    private String isbn;
    private String category;
    private Integer requestCount;
    private Integer status;
    private Integer requesterId;
    private String requesterName;
    private Integer purchaserId;
    private String purchaserName;
    private Integer logisticsId;
    private String logisticsName;
    private String requestNote;
    private String purchaseNote;
    private Boolean stockApplied;
    private Integer logisticsStatus;
    private String trackingNo;
    private String carrier;
    private String logisticsRemark;
    private Integer unreadCount;

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
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime logisticsUpdateTime;
}
