package org.darkroomlibrary.controller;

import jakarta.validation.Valid;
import jakarta.annotation.Resource;
import org.darkroomlibrary.aop.NormalizePageQuery;
import org.darkroomlibrary.aop.RequireRole;
import org.darkroomlibrary.domain.model.Notice;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.service.NoticeService;
import org.darkroomlibrary.web.dto.query.NoticePageQuery;
import org.darkroomlibrary.web.response.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公告管理接口。
 */
@RestController
@RequestMapping("/notice")
public class NoticeController {

    @Resource
    private NoticeService noticeService;

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/save")
    public ApiResponse<Void> save(@Valid @RequestBody Notice notice) {
        return noticeService.save(notice);
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/batchDelete")
    public ApiResponse<Void> batchDelete(@RequestBody List<Integer> ids) {
        return noticeService.batchDelete(ids);
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PutMapping("/update")
    public ApiResponse<Void> update(@Valid @RequestBody Notice notice) {
        return noticeService.update(notice);
    }

    @NormalizePageQuery
    @PostMapping("/query")
    public ApiResponse<List<Notice>> query(@RequestBody NoticePageQuery query) {
        return noticeService.query(query);
    }
}
