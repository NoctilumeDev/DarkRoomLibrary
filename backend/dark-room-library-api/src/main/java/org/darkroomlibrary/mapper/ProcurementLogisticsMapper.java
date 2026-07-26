package org.darkroomlibrary.mapper;

import org.darkroomlibrary.domain.model.ProcurementLogistics;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProcurementLogisticsMapper extends BaseMapper<ProcurementLogistics> {

    ProcurementLogistics getByOrderId(@Param("orderId") Integer orderId);
}
