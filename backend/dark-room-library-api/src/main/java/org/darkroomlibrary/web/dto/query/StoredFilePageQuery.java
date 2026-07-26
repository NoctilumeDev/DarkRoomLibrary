package org.darkroomlibrary.web.dto.query;

import org.darkroomlibrary.web.dto.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class StoredFilePageQuery extends PageQuery {

    private String fileName;
    private String originalName;
    private Integer uploaderId;
    private Integer status;
    private String refType;
}
