package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.mapper.MessageBoardMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.response.PageResponse;
import org.darkroomlibrary.web.dto.query.MessageBoardPageQuery;
import org.darkroomlibrary.web.dto.query.PageQuery;
import org.darkroomlibrary.domain.type.FileReferenceType;
import org.darkroomlibrary.domain.model.MessageBoard;
import org.darkroomlibrary.web.view.MessageBoardView;
import org.darkroomlibrary.service.ContentPostingPolicy;
import org.darkroomlibrary.service.MessageBoardService;
import org.darkroomlibrary.service.FileStorageService;
import org.darkroomlibrary.service.OperationAuditService;
import org.darkroomlibrary.service.support.RecycleBinPolicy;
import org.darkroomlibrary.utils.ContentSanitizer;
import org.darkroomlibrary.utils.IdListUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 留言板服务实现
 */
@Service
public class MessageBoardServiceImpl implements MessageBoardService {

    @Resource
    private MessageBoardMapper messageBoardMapper;

    @Resource
    private FileStorageService fileStorageService;

    @Resource
    private ContentPostingPolicy contentPostingPolicy;

    @Resource
    private OperationAuditService operationAuditService;

    @Resource
    private RecycleBinPolicy recycleBinPolicy;

    @Override
    @Transactional
    public ApiResponse<Void> save(MessageBoard messageBoard) {
        if (messageBoard == null) {
            return ApiResponse.error("留言参数不能为空");
        }
        String postingError = contentPostingPolicy.currentUserRejectionReason();
        if (postingError != null) {
            return ApiResponse.error(postingError);
        }
        String content = messageBoard.getContent();
        if (ContentSanitizer.exceedsLength(content, ContentSanitizer.MESSAGE_MAX_LENGTH)) {
            return ApiResponse.error("留言内容不能超过1000个字符");
        }
        String cleanContent = ContentSanitizer.plainText(content);
        boolean hasContent = cleanContent != null && !cleanContent.isEmpty();
        boolean hasAttachment = messageBoard.getAttachmentUrl() != null
                && !messageBoard.getAttachmentUrl().trim().isEmpty();
        if (!hasContent && !hasAttachment) {
            return ApiResponse.error("留言内容和附件不能同时为空");
        }
        if (hasAttachment) {
            if (!ContentSanitizer.isSafeMessageAttachment(
                    messageBoard.getAttachmentUrl(), messageBoard.getAttachmentType())) {
                return ApiResponse.error("附件地址或类型不合法");
            }
            if (ContentSanitizer.exceedsLength(
                    messageBoard.getAttachmentName(), ContentSanitizer.ATTACHMENT_NAME_MAX_LENGTH)) {
                return ApiResponse.error("附件名称不能超过255个字符");
            }
            String cleanAttachmentName = ContentSanitizer.plainText(messageBoard.getAttachmentName());
            if (cleanAttachmentName == null || cleanAttachmentName.isEmpty()) {
                return ApiResponse.error("附件名称不能为空");
            }
            messageBoard.setAttachmentUrl(messageBoard.getAttachmentUrl().trim());
            messageBoard.setAttachmentName(cleanAttachmentName);
            messageBoard.setAttachmentType(messageBoard.getAttachmentType().trim().toLowerCase());
        } else {
            messageBoard.setAttachmentUrl(null);
            messageBoard.setAttachmentName(null);
            messageBoard.setAttachmentType(null);
        }
        Integer userId = CurrentUserContext.userId();
        messageBoard.setUserId(userId);
        messageBoard.setContent(hasContent ? cleanContent : "");
        messageBoard.setReply(null);
        messageBoard.setIsDeleted(false);
        messageBoard.setModerationStatus(0);
        messageBoard.setCreateTime(LocalDateTime.now());
        if (messageBoardMapper.insert(messageBoard) != 1) {
            return ApiResponse.error("留言失败，请重试");
        }
        if (hasAttachment) {
            if (!fileStorageService.bindSingle(
                    messageBoard.getAttachmentUrl(), FileReferenceType.MESSAGE_ATTACHMENT, messageBoard.getId())) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return ApiResponse.error("附件文件无效或不属于当前用户");
            }
            String downloadUrl = fileStorageService.toDownloadUrl(messageBoard.getAttachmentUrl());
            if (messageBoardMapper.update(MessageBoard.builder()
                    .id(messageBoard.getId())
                    .attachmentUrl(downloadUrl)
                    .build()) == 0) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return ApiResponse.error("留言状态已变化，请重试");
            }
            messageBoard.setAttachmentUrl(downloadUrl);
        }
        return ApiResponse.success("留言成功");
    }

    @Override
    @Transactional
    public ApiResponse<Void> batchDelete(List<Integer> ids) {
        List<Integer> normalizedIds = IdListUtils.normalize(ids);
        if (normalizedIds.isEmpty()) {
            return ApiResponse.error("请选择要删除的留言");
        }
        if (IdListUtils.exceedsBatchLimit(normalizedIds)) {
            return ApiResponse.error("单次最多删除" + IdListUtils.MAX_BATCH_SIZE + "条留言");
        }
        List<MessageBoard> messages = messageBoardMapper.findByIdsForUpdate(normalizedIds);
        if (messages.size() != normalizedIds.size()) {
            return ApiResponse.error("部分留言不存在");
        }
        boolean adminOperation = CurrentUserContext.isAdministrator();
        int changed;
        if (adminOperation) {
            if (messages.stream().anyMatch(message -> !Objects.equals(message.getModerationStatus(), 0)
                    || Boolean.TRUE.equals(message.getIsDeleted()))) {
                return ApiResponse.error("部分留言已被处理，请刷新后重试");
            }
            changed = messageBoardMapper.hideByAdministrator(normalizedIds);
        } else {
            Integer currentUserId = CurrentUserContext.userId();
            for (MessageBoard message : messages) {
                if (!Objects.equals(message.getUserId(), currentUserId)) {
                    return ApiResponse.error("只能删除自己的留言");
                }
                if (Boolean.TRUE.equals(message.getIsDeleted())
                        || !Objects.equals(message.getModerationStatus(), 0)) {
                    return ApiResponse.error("部分留言状态已变化，请刷新后重试");
                }
            }
            LocalDateTime deletedAt = recycleBinPolicy.now();
            changed = messageBoardMapper.moveToRecycleBin(
                    normalizedIds,
                    currentUserId,
                    deletedAt,
                    recycleBinPolicy.restoreDeadline(deletedAt));
        }
        if (changed != normalizedIds.size()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error("留言状态已变化，请刷新后重试");
        }
        if (adminOperation) {
            fileStorageService.releaseReferences(FileReferenceType.MESSAGE_ATTACHMENT, normalizedIds);
            operationAuditService.record("审核", "留言",
                    "留言ID=" + normalizedIds + "，处理结果=移出公开留言；附件引用已释放");
            return ApiResponse.success("留言已移出公开区域");
        }
        operationAuditService.record("删除", "留言回收站", "留言ID=" + normalizedIds);
        return ApiResponse.success(
                "留言已移入回收站，可在" + recycleBinPolicy.retentionDays() + "天内恢复");
    }

    @Override
    public ApiResponse<List<MessageBoardView>> query(MessageBoardPageQuery dto) {
        List<MessageBoardView> list = messageBoardMapper.query(dto);
        sanitizeMessages(list);
        Integer total = messageBoardMapper.queryCount(dto);
        return PageResponse.success(list, total);
    }

    @Override
    public ApiResponse<List<MessageBoardView>> queryRecycleBin(PageQuery dto) {
        LocalDateTime now = recycleBinPolicy.now();
        Integer userId = CurrentUserContext.userId();
        List<MessageBoardView> list = messageBoardMapper.queryRecycleBin(
                userId, dto.getCurrent(), dto.getSize(), now);
        sanitizeMessages(list);
        return PageResponse.success(
                list, messageBoardMapper.queryRecycleBinCount(userId, now));
    }

    @Override
    @Transactional
    public ApiResponse<Void> restore(List<Integer> ids) {
        List<Integer> normalizedIds = IdListUtils.normalize(ids);
        if (normalizedIds.isEmpty()) {
            return ApiResponse.error("请选择要恢复的留言");
        }
        if (IdListUtils.exceedsBatchLimit(normalizedIds)) {
            return ApiResponse.error("单次最多恢复" + IdListUtils.MAX_BATCH_SIZE + "条留言");
        }
        Integer userId = CurrentUserContext.userId();
        List<MessageBoard> messages = messageBoardMapper.findByIdsForUpdate(normalizedIds);
        if (messages.size() != normalizedIds.size()) {
            return ApiResponse.error("部分留言不存在");
        }
        LocalDateTime now = recycleBinPolicy.now();
        for (MessageBoard message : messages) {
            if (!Objects.equals(message.getUserId(), userId)) {
                return ApiResponse.error("只能恢复自己的留言");
            }
            if (!Boolean.TRUE.equals(message.getIsDeleted())
                    || !Objects.equals(message.getModerationStatus(), 0)
                    || message.getExpiredAt() != null
                    || message.getRestoreDeadline() == null
                    || !message.getRestoreDeadline().isAfter(now)) {
                return ApiResponse.error("留言已不可恢复");
            }
        }
        if (messageBoardMapper.restoreFromRecycleBin(normalizedIds, userId, now)
                != normalizedIds.size()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error("留言状态已变化，请刷新后重试");
        }
        return ApiResponse.success("留言已恢复");
    }

    @Override
    @Transactional
    public ApiResponse<Void> reply(Integer id, String reply) {
        MessageBoard msg = id == null ? null : messageBoardMapper.findByIdForUpdate(id);
        if (msg == null || Boolean.TRUE.equals(msg.getIsDeleted())
                || !Objects.equals(msg.getModerationStatus(), 0)) {
            return ApiResponse.error("留言不存在");
        }
        if (ContentSanitizer.exceedsLength(reply, ContentSanitizer.MESSAGE_REPLY_MAX_LENGTH)) {
            return ApiResponse.error("回复内容不能超过1000个字符");
        }
        String cleanReply = ContentSanitizer.plainText(reply);
        if (cleanReply == null || cleanReply.isEmpty()) {
            return ApiResponse.error("回复内容不能为空");
        }
        if (messageBoardMapper.update(MessageBoard.builder().id(id).reply(cleanReply).build()) == 0) {
            return ApiResponse.error("留言状态已变化，请刷新后重试");
        }
        return ApiResponse.success("回复成功");
    }

    private void sanitizeMessages(List<MessageBoardView> list) {
        for (MessageBoardView item : list) {
            item.setContent(ContentSanitizer.plainText(item.getContent()));
            item.setReply(ContentSanitizer.plainText(item.getReply()));
            item.setAttachmentName(ContentSanitizer.plainText(item.getAttachmentName()));
            item.setAttachmentUrl(fileStorageService.toDownloadUrl(item.getAttachmentUrl()));
            if (item.getAttachmentUrl() != null
                    && !ContentSanitizer.isSafeMessageAttachment(
                    item.getAttachmentUrl(), item.getAttachmentType())) {
                item.setAttachmentUrl(null);
                item.setAttachmentName(null);
                item.setAttachmentType(null);
            }
        }
    }
}
