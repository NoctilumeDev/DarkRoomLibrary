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
 * 图书评价点赞实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BookReviewLike {
    /**
     * 点赞ID
     */
    @TableId(type = IdType.AUTO)
    private Integer id;
    /**
     * 评价ID
     */
    private Integer reviewId;
    /**
     * 用户ID
     */
    private Integer userId;
    /**
     * 点赞时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
