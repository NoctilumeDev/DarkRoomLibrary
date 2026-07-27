package org.darkroomlibrary.mapper;

import org.darkroomlibrary.web.dto.query.ProcurementOrderPageQuery;
import org.darkroomlibrary.domain.model.ProcurementOrder;
import org.darkroomlibrary.web.view.ProcurementOrderView;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProcurementOrderMapper extends BaseMapper<ProcurementOrder> {

    default ProcurementOrder getById(Integer id) { return selectById(id); }
    default int update(ProcurementOrder entity) { return updateById(entity); }

    ProcurementOrder findByIdForUpdate(@Param("id") Integer id);

    List<ProcurementOrderView> query(ProcurementOrderPageQuery dto);

    Integer queryCount(ProcurementOrderPageQuery dto);

    int countActiveByBookIds(@Param("bookIds") List<Integer> bookIds);

    int countActiveByUserIds(@Param("userIds") List<Integer> userIds);

    int markStockApplied(@Param("id") Integer id);
}
