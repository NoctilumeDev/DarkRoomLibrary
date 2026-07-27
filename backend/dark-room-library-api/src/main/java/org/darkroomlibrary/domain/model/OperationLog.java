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
 * 操作日志实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OperationLog {
    /**
     * 日志ID
     */
    @TableId(type = IdType.AUTO)
    private Integer id;
    /**
     * 审计记录幂等键
     */
    private String eventKey;
    /**
     * 操作用户ID
     */
    private Integer userId;
    /**
     * 操作用户名
     */
    private String userName;
    /**
     * 操作类型
     */
    private String operation;
    /**
     * 操作目标
     */
    private String target;
    /**
     * 操作详情
     */
    private String detail;
    /**
     * IP地址
     */
    private String ip;
    /**
     * 操作时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
