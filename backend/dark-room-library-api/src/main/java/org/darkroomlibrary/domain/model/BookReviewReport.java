package org.darkroomlibrary.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 图书评价举报实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BookReviewReport {
    /**
     * 举报ID
     */
    @TableId(type = IdType.AUTO)
    private Integer id;
    /**
     * 评价ID
     */
    private Integer reviewId;
    /**
     * 举报用户ID
     */
    private Integer userId;
    /**
     * 举报原因
     */
    private String reason;
    /**
     * 状态：0=待处理，1=已处理，2=忽略
     */
    private Integer status;
    /**
     * 举报时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    /**
     * 处理时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime handleTime;
}
