package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.mapper.NoticeMapper;
import org.darkroomlibrary.mapper.UserMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.NoticePageQuery;
import org.darkroomlibrary.web.dto.query.UserPageQuery;
import org.darkroomlibrary.web.view.MetricPoint;
import org.darkroomlibrary.service.DashboardSummaryService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

@Service
public class DashboardSummaryServiceImpl implements DashboardSummaryService {

    @Resource
    private UserMapper userMapper;
    @Resource
    private NoticeMapper noticeMapper;

    @Override
    public ApiResponse<List<MetricPoint>> staticControls() {
        int users = userMapper.queryCount(new UserPageQuery());
        int notices = noticeMapper.queryCount(new NoticePageQuery());
        return ApiResponse.success(List.of(
                new MetricPoint("存量用户（个）", users),
                new MetricPoint("公告（篇）", notices)
        ));
    }
}
