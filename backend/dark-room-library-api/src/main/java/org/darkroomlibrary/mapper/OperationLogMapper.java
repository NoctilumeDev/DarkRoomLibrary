package org.darkroomlibrary.mapper;

import org.darkroomlibrary.web.dto.query.OperationLogPageQuery;
import org.darkroomlibrary.domain.model.OperationLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {

    List<OperationLog> query(OperationLogPageQuery dto);

    Integer queryCount(OperationLogPageQuery dto);
}
