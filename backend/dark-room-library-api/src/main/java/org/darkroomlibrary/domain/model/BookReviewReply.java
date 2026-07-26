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
 * 图书评价回复实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BookReviewReply {
    /**
     * 回复ID
     */
    @TableId(type = IdType.AUTO)
    private Integer id;
    /**
     * 评价ID
     */
    private Integer reviewId;
    /**
     * 回复用户ID
     */
    private Integer userId;
    /**
     * 被回复用户ID
     */
    private Integer replyToUserId;
    /**
     * 回复内容
     */
    private String content;
    /**
     * 回复时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
