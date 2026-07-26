package org.darkroomlibrary.web.view;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 图书收藏VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookFavoriteView {
    /**
     * 收藏ID
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
     * 收藏时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    /**
     * 图书名称
     */
    private String bookName;
    /**
     * 图书作者
     */
    private String bookAuthor;
    /**
     * 图书封面
     */
    private String bookCover;
    /**
     * 可借数量
     */
    private Integer availableCount;
}