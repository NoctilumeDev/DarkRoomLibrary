package org.darkroomlibrary.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

@Mapper
public interface AdminWorkflowMapper {

    Map<String, Object> countWorkflowStatuses();
}
