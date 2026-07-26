package org.darkroomlibrary.web.view;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 图书评价回复VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookReviewReplyView {
    private Integer id;
    private Integer reviewId;
    private Integer userId;
    private Integer replyToUserId;
    private String content;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    private String userName;
    private String replyToUserName;
}
