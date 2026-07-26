package org.darkroomlibrary.web.view;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 图书预约VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookReservationView {
    /**
     * 预约ID
     */
    private Integer id;
    /**
     * 用户ID
     */
    private Integer userId;
    /**
     * 图书ID
     */
    private Integer bookId;
    /**
     * 预约时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reserveTime;
    /**
     * 状态(0:预约中, 1:已借阅, 2:已取消)
     */
    private Integer status;
    /**
     * 通知时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime notifyTime;
    /**
     * 用户名
     */
    private String userName;
    /**
     * 图书名称
     */
    private String bookName;
}