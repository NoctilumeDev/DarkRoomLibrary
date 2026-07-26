package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.mapper.BookMapper;
import org.darkroomlibrary.mapper.BookReservationMapper;
import org.darkroomlibrary.mapper.UserMapper;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.domain.model.BookReservation;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.service.NotificationService;
import org.darkroomlibrary.service.ReservationWorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class ReservationWorkflowServiceImpl implements ReservationWorkflowService {

    @Resource
    private BookReservationMapper bookReservationMapper;

    @Resource
    private BookMapper bookMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private NotificationService notificationService;

    @Override
    @Transactional
    public void onBookReturned(Integer bookId) {
        Book book = bookMapper.getById(bookId);
        if (book == null || book.getAvailableCount() == null || book.getAvailableCount() <= 0) {
            return;
        }
        BookReservation reservation = bookReservationMapper.findFirstWaitingByBookIdForUpdate(bookId);
        if (reservation == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (bookReservationMapper.markNotified(reservation.getId(), now) == 0) {
            return;
        }

        User user = userMapper.getById(reservation.getUserId());
        if (user == null) {
            log.warn("预约通知缺少用户或图书信息: reservationId={}, bookId={}", reservation.getId(), bookId);
            return;
        }
        notificationService.enqueueEmail(
                user.getUserEmail(),
                "【图书管理系统】预约到货通知",
                "您预约的《" + book.getName() + "》已可借阅，请在48小时内处理，逾期将自动释放预约。"
        );
    }

    @Override
    @Scheduled(fixedDelay = 600000)
    @Transactional
    public void expireOverdueNotifications() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(48);
        List<Integer> releasedBookIds = bookReservationMapper.findExpiredNotifiedBookIds(cutoff);
        int expired = bookReservationMapper.expireNotifiedBefore(cutoff);
        if (expired > 0) {
            log.info("已释放过期预约通知: count={}", expired);
            for (Integer bookId : releasedBookIds) {
                onBookReturned(bookId);
            }
        }
    }
}
