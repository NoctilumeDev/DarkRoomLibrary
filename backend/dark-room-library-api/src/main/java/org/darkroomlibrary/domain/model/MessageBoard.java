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
 * 留言板实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageBoard {
    /**
     * 留言ID
     */
    @TableId(type = IdType.AUTO)
    private Integer id;
    /**
     * 用户ID
     */
    private Integer userId;
    /**
     * 留言内容
     */
    private String content;
    /**
     * 附件地址
     */
    private String attachmentUrl;
    /**
     * 附件原始名称
     */
    private String attachmentName;
    /**
     * 附件类型
     */
    private String attachmentType;
    /**
     * 留言时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    /**
     * 管理员回复
     */
    private String reply;
}
