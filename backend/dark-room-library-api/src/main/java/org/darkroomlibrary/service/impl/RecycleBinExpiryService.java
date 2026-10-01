package org.darkroomlibrary.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.darkroomlibrary.domain.model.MessageBoard;
import org.darkroomlibrary.domain.type.FileReferenceType;
import org.darkroomlibrary.mapper.BookMapper;
import org.darkroomlibrary.mapper.BookReviewMapper;
import org.darkroomlibrary.mapper.MessageBoardMapper;
import org.darkroomlibrary.service.FileStorageService;
import org.darkroomlibrary.service.support.RecycleBinPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Revokes restore authority after the configured retention window.
 *
 * <p>Rows are retained as business tombstones. Message attachments are released only after the
 * same locked transition that marks a reader-deleted message as expired, so a stale cleanup worker
 * cannot delete an attachment belonging to a message that has already been restored.</p>
 */
@Slf4j
@Service
public class RecycleBinExpiryService {

    private final BookMapper bookMapper;
    private final BookReviewMapper bookReviewMapper;
    private final MessageBoardMapper messageBoardMapper;
    private final FileStorageService fileStorageService;
    private final TransactionTemplate transactionTemplate;
    private final RecycleBinPolicy recycleBinPolicy;
    private final int cleanupBatchSize;

    public RecycleBinExpiryService(
            BookMapper bookMapper,
            BookReviewMapper bookReviewMapper,
            MessageBoardMapper messageBoardMapper,
            FileStorageService fileStorageService,
            TransactionTemplate transactionTemplate,
            RecycleBinPolicy recycleBinPolicy,
            @Value("${recycle-bin.cleanup-batch-size:100}") int cleanupBatchSize) {
        this.bookMapper = bookMapper;
        this.bookReviewMapper = bookReviewMapper;
        this.messageBoardMapper = messageBoardMapper;
        this.fileStorageService = fileStorageService;
        this.transactionTemplate = transactionTemplate;
        this.recycleBinPolicy = recycleBinPolicy;
        this.cleanupBatchSize = Math.max(1, Math.min(cleanupBatchSize, 1000));
    }

    @Scheduled(cron = "${recycle-bin.cleanup-cron:0 15 3 * * ?}")
    public void scheduledCleanup() {
        CleanupResult result = cleanupExpiredEntries();
        log.info("回收站恢复资格清理完成: books={}, reviews={}, messages={}",
                result.books(), result.reviews(), result.messages());
    }

    public CleanupResult cleanupExpiredEntries() {
        LocalDateTime now = recycleBinPolicy.now();
        int[] generalCounts = transactionTemplate.execute(status -> new int[]{
                bookMapper.markExpired(now),
                bookReviewMapper.markExpired(now)
        });
        int expiredMessages = 0;
        List<Integer> candidates = messageBoardMapper.findExpirationCandidates(now, cleanupBatchSize);
        for (Integer id : candidates) {
            Boolean expired = transactionTemplate.execute(status -> expireMessage(id, now));
            if (Boolean.TRUE.equals(expired)) {
                expiredMessages++;
            }
        }
        return new CleanupResult(generalCounts[0], generalCounts[1], expiredMessages);
    }

    private boolean expireMessage(Integer id, LocalDateTime now) {
        MessageBoard message = messageBoardMapper.findByIdForUpdate(id);
        if (message == null
                || !Boolean.TRUE.equals(message.getIsDeleted())
                || !Objects.equals(message.getModerationStatus(), 0)
                || message.getExpiredAt() != null
                || message.getRestoreDeadline() == null
                || message.getRestoreDeadline().isAfter(now)) {
            return false;
        }
        if (messageBoardMapper.markExpired(id, message.getRestoreDeadline(), now) != 1) {
            return false;
        }
        fileStorageService.releaseReference(FileReferenceType.MESSAGE_ATTACHMENT, id);
        return true;
    }

    public record CleanupResult(int books, int reviews, int messages) {
    }
}
