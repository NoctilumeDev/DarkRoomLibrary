package org.darkroomlibrary.service.impl;

import jakarta.annotation.Resource;
import org.darkroomlibrary.mapper.NoticeMapper;
import org.darkroomlibrary.domain.model.Notice;
import org.darkroomlibrary.domain.type.FileReferenceType;
import org.darkroomlibrary.service.FileStorageService;
import org.darkroomlibrary.service.NoticeService;
import org.darkroomlibrary.utils.ContentSanitizer;
import org.darkroomlibrary.utils.IdListUtils;
import org.darkroomlibrary.web.dto.query.NoticePageQuery;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.response.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公告的写入、安全清洗、文件引用和分页查询。
 */
@Service
public class NoticeServiceImpl implements NoticeService {

    @Resource
    private NoticeMapper noticeMapper;

    @Resource
    private FileStorageService fileStorageService;

    @Override
    @Transactional
    public ApiResponse<Void> save(Notice notice) {
        String validationError = sanitizeAndValidate(notice);
        if (validationError != null) {
            return ApiResponse.error(validationError);
        }
        notice.setCreateTime(LocalDateTime.now());
        if (noticeMapper.insert(notice) != 1) {
            return ApiResponse.error("公告发布失败，请重试");
        }
        if (!bindAssets(notice)) {
            return invalidAssetResponse();
        }
        return ApiResponse.success();
    }

    @Override
    @Transactional
    public ApiResponse<Void> batchDelete(List<Integer> ids) {
        List<Integer> normalizedIds = IdListUtils.normalize(ids);
        if (normalizedIds.isEmpty()) {
            return ApiResponse.error("请选择要删除的公告");
        }
        if (IdListUtils.exceedsBatchLimit(normalizedIds)) {
            return ApiResponse.error("单次最多删除" + IdListUtils.MAX_BATCH_SIZE + "条公告");
        }
        if (noticeMapper.findByIdsForUpdate(normalizedIds).size() != normalizedIds.size()) {
            return ApiResponse.error("部分公告不存在");
        }
        fileStorageService.releaseReferences(FileReferenceType.NOTICE_ASSET, normalizedIds);
        if (noticeMapper.deleteByIds(normalizedIds) != normalizedIds.size()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error("公告状态已变化，请刷新后重试");
        }
        return ApiResponse.success();
    }

    @Override
    @Transactional
    public ApiResponse<Void> update(Notice notice) {
        String validationError = sanitizeAndValidate(notice);
        if (validationError != null) {
            return ApiResponse.error(validationError);
        }
        if (notice.getId() == null || noticeMapper.findByIdForUpdate(notice.getId()) == null) {
            return ApiResponse.error("公告不存在");
        }
        if (noticeMapper.updateById(notice) == 0) {
            return ApiResponse.error("公告状态已变化，请刷新后重试");
        }
        if (!bindAssets(notice)) {
            return invalidAssetResponse();
        }
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<List<Notice>> query(NoticePageQuery query) {
        List<Notice> notices = noticeMapper.findPage(query);
        notices.forEach(this::sanitizeForResponse);
        return PageResponse.success(notices, noticeMapper.countMatching(query));
    }

    private boolean bindAssets(Notice notice) {
        return fileStorageService.bindFromHtml(
                notice.getContent(),
                FileReferenceType.NOTICE_ASSET,
                notice.getId()
        );
    }

    private ApiResponse<Void> invalidAssetResponse() {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return ApiResponse.error("公告中的文件无效或不属于当前用户");
    }

    private void sanitizeForResponse(Notice notice) {
        notice.setName(ContentSanitizer.plainText(notice.getName()));
        notice.setContent(ContentSanitizer.richText(notice.getContent()));
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
