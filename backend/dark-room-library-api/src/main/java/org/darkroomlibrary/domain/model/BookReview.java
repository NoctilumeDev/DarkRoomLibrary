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
 * 图书评价实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BookReview {
    /**
     * 评价ID
     */
    @TableId(type = IdType.AUTO)
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
     * 评分(1-5)
     */
    private Integer rating;
    /**
     * 评价内容
     */
    private String content;
    /**
     * 状态：0=正常，1=隐藏
     */
    private Integer status;
    /**
     * 评价时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
