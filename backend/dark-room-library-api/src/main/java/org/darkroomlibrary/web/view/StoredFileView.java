package org.darkroomlibrary.web.view;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StoredFileView {

    private String fileName;
    private String originalName;
    private String extension;
    private String contentType;
    private Long fileSize;
    private Integer uploaderId;
    private String uploaderName;
    private Integer status;
    private String refType;
    private Integer refId;
    private Boolean diskExists;
    private String accessUrl;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime bindTime;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
