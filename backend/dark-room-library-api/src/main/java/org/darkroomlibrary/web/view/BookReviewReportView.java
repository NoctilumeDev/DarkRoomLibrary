package org.darkroomlibrary.web.view;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 图书评价举报审核VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookReviewReportView {
    private Integer id;
    private Integer reviewId;
    private Integer reportUserId;
    private String reportUserName;
    private Integer reviewUserId;
    private String reviewUserName;
    private Integer bookId;
    private String bookName;
    private Integer rating;
    private String reviewContent;
    private Integer reviewStatus;
    private String reason;
    private Integer status;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime handleTime;
}
