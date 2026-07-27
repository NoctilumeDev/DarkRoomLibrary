package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.mapper.BookMapper;
import org.darkroomlibrary.mapper.BookReservationMapper;
import org.darkroomlibrary.mapper.UserMapper;
import org.darkroomlibrary.domain.type.AccountStatus;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.domain.model.BookReservation;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.service.NotificationService;
import org.darkroomlibrary.service.ReservationWorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class ReservationWorkflowServiceImpl implements ReservationWorkflowService {

    private static final int RECONCILE_BATCH_SIZE = 100;
    private static final int EXPIRATION_BATCH_SIZE = 100;

    @Resource
    private BookReservationMapper bookReservationMapper;

    @Resource
    private BookMapper bookMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private NotificationService notificationService;

    @Resource
    private PlatformTransactionManager transactionManager;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookReturned(Integer bookId) {
        notifyReservationsForBook(bookId);
    }

    private void notifyReservationsForBook(Integer bookId) {
        Book book = bookMapper.findByIdForUpdate(bookId);
        if (book == null || book.getAvailableCount() == null || book.getAvailableCount() <= 0) {
            return;
        }
        notifyAvailableReservations(book);
    }

    private void notifyAvailableReservations(Book book) {
        Integer bookId = book.getId();
        int notifiedCount = bookReservationMapper.countNotifiedByBookId(bookId);
        int remainingCapacity = book.getAvailableCount() - notifiedCount;
        while (remainingCapacity > 0) {
            BookReservation reservation = bookReservationMapper.findFirstWaitingByBookIdForUpdate(bookId);
            if (reservation == null) {
                return;
            }
            User user = userMapper.getById(reservation.getUserId());
            if (!canReceiveReservation(user)) {
                if (bookReservationMapper.expireWaiting(reservation.getId()) == 0) {
                    return;
                }
                log.warn("预约用户不可用，已释放排队记录: reservationId={}, bookId={}, userId={}",
                        reservation.getId(), bookId, reservation.getUserId());
                continue;
            }
            LocalDateTime now = LocalDateTime.now();
            if (bookReservationMapper.markNotified(reservation.getId(), now) == 0) {
                return;
            }
            notificationService.enqueueEmail(
                    user.getUserEmail(),
                    "【暗室藏书】预约到货通知",
                    "您预约的《" + book.getName() + "》已可借阅，请在48小时内处理，逾期将自动释放预约。"
            );
            remainingCapacity--;
        }
    }

    @Override
    @Scheduled(fixedDelay = 600000)
    public void expireOverdueNotifications() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(48);
        List<Integer> releasedBookIds =
                bookReservationMapper.findExpiredNotifiedBookIds(cutoff, EXPIRATION_BATCH_SIZE);
        int expiredTotal = 0;
        for (Integer bookId : releasedBookIds) {
            try {
                expiredTotal += expireBookNotifications(bookId, cutoff);
            } catch (Exception e) {
                log.warn("释放过期预约失败，将由下次任务重试: bookId={}, error={}",
                        bookId, e.getMessage());
            }
        }
        if (expiredTotal > 0) {
            log.info("已释放过期预约通知: count={}", expiredTotal);
        }
    }

    @Scheduled(fixedDelayString = "${reservation.reconcile-delay-ms:60000}")
    public void reconcileAvailableReservations() {
        List<Integer> bookIds =
                bookReservationMapper.findBooksNeedingNotification(RECONCILE_BATCH_SIZE);
        for (Integer bookId : bookIds) {
            try {
                TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
                transactionTemplate.executeWithoutResult(status -> notifyReservationsForBook(bookId));
            } catch (Exception e) {
                log.warn("预约库存对账失败，将由下次任务重试: bookId={}, error={}",
                        bookId, e.getMessage());
            }
        }
    }

    private int expireBookNotifications(Integer bookId, LocalDateTime cutoff) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        Integer expired = transactionTemplate.execute(status -> {
            Book book = bookMapper.findByIdForUpdate(bookId);
            if (book == null) {
                return 0;
            }
            int count = bookReservationMapper.expireNotifiedByBookIdBefore(bookId, cutoff);
            if (count > 0 && book.getAvailableCount() != null && book.getAvailableCount() > 0) {
                notifyAvailableReservations(book);
            }
            return count;
        });
        return expired == null ? 0 : expired;
    }

    private boolean canReceiveReservation(User user) {
        return user != null
                && Objects.equals(user.getAccountStatus(), AccountStatus.NORMAL.code())
                && !Boolean.TRUE.equals(user.getIsLogin())
                && user.getUserEmail() != null
                && !user.getUserEmail().trim().isEmpty();
    }
}
