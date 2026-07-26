package org.darkroomlibrary.service;

import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.MessageBoardPageQuery;
import org.darkroomlibrary.domain.model.MessageBoard;
import org.darkroomlibrary.web.view.MessageBoardView;

import java.util.List;

/**
 * 留言板服务接口
 */
public interface MessageBoardService {

    ApiResponse<Void> save(MessageBoard messageBoard);

    ApiResponse<Void> batchDelete(List<Integer> ids);

    ApiResponse<List<MessageBoardView>> query(MessageBoardPageQuery dto);

    ApiResponse<Void> reply(Integer id, String reply);
}