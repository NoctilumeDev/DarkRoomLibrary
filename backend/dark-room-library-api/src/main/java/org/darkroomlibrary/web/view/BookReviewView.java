package org.darkroomlibrary.web.view;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 图书评价VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookReviewView {
    /**
     * 评价ID
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
    /**
     * 用户名
     */
    private String userName;
    /**
     * 图书名称
     */
    private String bookName;
    /**
     * 点赞数量
     */
    private Integer likeCount;
    /**
     * 当前用户是否已点赞
     */
    private Boolean liked;
    /**
     * 举报数量
     */
    private Integer reportCount;
    /**
     * 当前用户是否已举报
     */
    private Boolean reported;
    /**
     * 一级回复列表
     */
    private List<BookReviewReplyView> replies;
}
