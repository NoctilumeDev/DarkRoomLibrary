package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.mapper.MessageBoardMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.response.PageResponse;
import org.darkroomlibrary.web.dto.query.MessageBoardPageQuery;
import org.darkroomlibrary.domain.type.FileReferenceType;
import org.darkroomlibrary.domain.model.MessageBoard;
import org.darkroomlibrary.web.view.MessageBoardView;
import org.darkroomlibrary.service.ContentPostingPolicy;
import org.darkroomlibrary.service.MessageBoardService;
import org.darkroomlibrary.service.FileStorageService;
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
        // 非管理员只能删除自己的留言
        if (!CurrentUserContext.isAdministrator()) {
            Integer currentUserId = CurrentUserContext.userId();
            for (MessageBoard message : messages) {
                if (!Objects.equals(message.getUserId(), currentUserId)) {
                    return ApiResponse.error("只能删除自己的留言");
                }
            }
        }
        fileStorageService.releaseReferences(FileReferenceType.MESSAGE_ATTACHMENT, normalizedIds);
        if (messageBoardMapper.batchDelete(normalizedIds) != normalizedIds.size()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error("留言状态已变化，请刷新后重试");
        }
        return ApiResponse.success("删除留言成功");
    }

    @Override
    public ApiResponse<List<MessageBoardView>> query(MessageBoardPageQuery dto) {
        List<MessageBoardView> list = messageBoardMapper.query(dto);
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
        Integer total = messageBoardMapper.queryCount(dto);
        return PageResponse.success(list, total);
    }

    @Override
    @Transactional
    public ApiResponse<Void> reply(Integer id, String reply) {
        MessageBoard msg = id == null ? null : messageBoardMapper.findByIdForUpdate(id);
        if (msg == null) {
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
}
