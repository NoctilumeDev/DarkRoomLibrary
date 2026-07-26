package org.darkroomlibrary.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.darkroomlibrary.mapper.NoticeMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.response.PageResponse;
import org.darkroomlibrary.web.dto.query.NoticePageQuery;
import org.darkroomlibrary.domain.type.FileReferenceType;
import org.darkroomlibrary.domain.model.Notice;
import org.darkroomlibrary.service.FileStorageService;
import org.darkroomlibrary.service.NoticeService;
import org.darkroomlibrary.utils.ContentSanitizer;
import org.darkroomlibrary.utils.IdListUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 公告业务逻辑实现
 */
@Slf4j
@Service
public class NoticeServiceImpl implements NoticeService {

    @Resource
    private NoticeMapper noticeMapper;

    @Resource
    private FileStorageService fileStorageService;

    /**
     * 公告新增
     *
     * @param notice 参数
     * @return ApiResponse<Void>
     */
    @Override
    @Transactional
    public ApiResponse<Void> save(Notice notice) {
        String validationError = sanitizeAndValidate(notice);
        if (validationError != null) {
            return ApiResponse.error(validationError);
        }
        notice.setCreateTime(LocalDateTime.now());
        noticeMapper.save(notice);
        if (!fileStorageService.bindFromHtml(
                notice.getContent(), FileReferenceType.NOTICE_ASSET, notice.getId())) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error("公告中的文件无效或不属于当前用户");
        }
        return ApiResponse.success();
    }

    /**
     * 公告删除
     *
     * @param ids 参数
     * @return ApiResponse<Void>
     */
    @Override
    @Transactional
    public ApiResponse<Void> batchDelete(List<Integer> ids) {
        List<Integer> normalizedIds = IdListUtils.normalize(ids);
        if (normalizedIds.isEmpty()) {
            return ApiResponse.error("请选择要删除的公告");
        }
        fileStorageService.releaseReferences(FileReferenceType.NOTICE_ASSET, normalizedIds);
        noticeMapper.batchDelete(normalizedIds);
        return ApiResponse.success();
    }

    /**
     * 公告修改
     *
     * @param notice 参数
     * @return ApiResponse<Void>
     */
    @Override
    @Transactional
    public ApiResponse<Void> update(Notice notice) {
        String validationError = sanitizeAndValidate(notice);
        if (validationError != null) {
            return ApiResponse.error(validationError);
        }
        if (noticeMapper.getById(notice.getId()) == null) {
            return ApiResponse.error("公告不存在");
        }
        noticeMapper.update(notice);
        if (!fileStorageService.bindFromHtml(
                notice.getContent(), FileReferenceType.NOTICE_ASSET, notice.getId())) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error("公告中的文件无效或不属于当前用户");
        }
        return ApiResponse.success();
    }

    /**
     * 公告查询
     *
     * @param noticePageQuery 查询参数
     * @return ApiResponse<List < Notice>>
     */
    @Override
    public ApiResponse<List<Notice>> query(NoticePageQuery noticePageQuery) {
        List<Notice> noticeList = noticeMapper.query(noticePageQuery);
        for (Notice notice : noticeList) {
            notice.setName(ContentSanitizer.plainText(notice.getName()));
            notice.setContent(ContentSanitizer.richText(notice.getContent()));
        }
        Integer totalCount = noticeMapper.queryCount(noticePageQuery);
        return PageResponse.success(noticeList, totalCount);
    }

    private String sanitizeAndValidate(Notice notice) {
        if (notice == null) {
            return "公告参数不能为空";
        }
        if (ContentSanitizer.exceedsLength(
                notice.getName(), ContentSanitizer.NOTICE_TITLE_MAX_LENGTH)) {
            return "公告标题不能超过100个字符";
        }
        String cleanName = ContentSanitizer.plainText(notice.getName());
        if (cleanName == null || cleanName.isEmpty()) {
            return "公告标题不能为空";
        }
        if (ContentSanitizer.exceedsLength(
                notice.getContent(), ContentSanitizer.NOTICE_HTML_MAX_LENGTH)) {
            return "公告内容不能超过20000个字符";
        }
        String cleanContent = ContentSanitizer.richText(notice.getContent());
        if (!ContentSanitizer.hasVisibleRichText(cleanContent)) {
            return "公告内容不能为空";
        }
        notice.setName(cleanName);
        notice.setContent(cleanContent);
        return null;
    }

}
