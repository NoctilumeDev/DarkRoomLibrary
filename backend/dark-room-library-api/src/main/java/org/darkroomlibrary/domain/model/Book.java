package org.darkroomlibrary.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 图书实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Book {
    /**
     * 图书ID
     */
    @TableId(type = IdType.AUTO)
    private Integer id;
    /**
     * 图书行版本。管理端编辑携带该值，避免旧表单覆盖并发修改。
     */
    private Integer version;
    /**
     * 图书名称
     */
    @NotBlank(message = "图书名称不能为空")
    @Size(max = 100, message = "图书名称不能超过100个字符")
    private String name;
    /**
     * 作者
     */
    @NotBlank(message = "作者不能为空")
    @Size(max = 100, message = "作者不能超过100个字符")
    private String author;
    /**
     * ISBN号
     */
    @Pattern(regexp = "^$|^\\d{13}$|^\\d{10}$", message = "ISBN格式不正确")
    @Size(max = 20, message = "ISBN不能超过20个字符")
    private String isbn;
    /**
     * 出版社
     */
    @Size(max = 100, message = "出版社不能超过100个字符")
    private String publisher;
    /**
     * 分类
     */
    @Size(max = 50, message = "分类不能超过50个字符")
    private String category;
    /**
     * 总数量
     */
    private Integer totalCount;
    /**
     * 可借数量
     */
    private Integer availableCount;
    /**
     * 编辑表单加载时的总库存快照，用于阻止旧表单覆盖并发库存变化。
     */
    @TableField(exist = false)
    private Integer originalTotalCount;
    /**
     * 编辑表单加载时的可借库存快照，用于阻止旧表单覆盖并发库存变化。
     */
    @TableField(exist = false)
    private Integer originalAvailableCount;
    /**
     * 封面
     */
    @Size(max = 500, message = "封面地址不能超过500个字符")
    private String cover;
    /**
     * 简介
     */
    private String description;
    /**
     * 创建时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    /**
     * 软删除标记(0:正常,1:已删除)
     */
    private Boolean isDeleted;
    /**
     * 所在书架ID
     */
    private Integer bookshelfId;
}
