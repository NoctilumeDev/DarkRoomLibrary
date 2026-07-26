package org.darkroomlibrary.controller;

import org.darkroomlibrary.aop.NormalizePageQuery;
import org.darkroomlibrary.aop.RequireRole;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.NoticePageQuery;
import org.darkroomlibrary.domain.model.Notice;
import org.darkroomlibrary.service.NoticeService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;

/**
 * 公告的 Controller
 */
@RestController
@RequestMapping(value = "/notice")
public class NoticeController {

    @Resource
    private NoticeService noticeService;

    /**
     * 公告新增
     *
     * @param notice 新增数据
     * @return ApiResponse<Void> 通用响应体
     */
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping(value = "/save")
    public ApiResponse<Void> save(@Valid @RequestBody Notice notice) {
        return noticeService.save(notice);
    }

    /**
     * 公告删除
     *
     * @param ids 要删除的公告ID列表
     * @return ApiResponse<Void> 通用响应体
     */
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping(value = "/batchDelete")
    public ApiResponse<Void> batchDelete(@RequestBody List<Integer> ids) {
        return noticeService.batchDelete(ids);
    }

    /**
     * 公告修改
     *
     * @param notice 参数
     * @return ApiResponse<Void> 响应
     */
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PutMapping(value = "/update")
    public ApiResponse<Void> update(@Valid @RequestBody Notice notice) {
        return noticeService.update(notice);
    }

    /**
     * 公告查询
     *
     * @param noticePageQuery 查询参数
     * @return ApiResponse<List < Notice>> 通用响应
     */
    @NormalizePageQuery
    @PostMapping(value = "/query")
    public ApiResponse<List<Notice>> query(@RequestBody NoticePageQuery noticePageQuery) {
        return noticeService.query(noticePageQuery);
    }

}
