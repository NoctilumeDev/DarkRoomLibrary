package org.darkroomlibrary.mapper;

import org.darkroomlibrary.web.dto.query.MessageBoardPageQuery;
import org.darkroomlibrary.domain.model.MessageBoard;
import org.darkroomlibrary.web.view.MessageBoardView;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MessageBoardMapper extends BaseMapper<MessageBoard> {

    default MessageBoard getById(Integer id) { return selectById(id); }
    default int update(MessageBoard entity) { return updateById(entity); }
    default int batchDelete(List<Integer> ids) { return deleteByIds(ids); }

    List<MessageBoardView> query(MessageBoardPageQuery dto);

    Integer queryCount(MessageBoardPageQuery dto);
}
