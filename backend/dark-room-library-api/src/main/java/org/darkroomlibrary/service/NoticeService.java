package org.darkroomlibrary.service;

import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.NoticePageQuery;
import org.darkroomlibrary.domain.model.Notice;

import java.util.List;

/**
 * 公告业务能力。
 */
public interface NoticeService {

    ApiResponse<Void> save(Notice notice);

    ApiResponse<Void> batchDelete(List<Integer> ids);

    ApiResponse<Void> update(Notice notice);

    ApiResponse<List<Notice>> query(NoticePageQuery query);
}
