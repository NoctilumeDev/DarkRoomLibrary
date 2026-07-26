package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.domain.model.BookReservation;
import org.darkroomlibrary.domain.model.BorrowRecord;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.service.BookReservationService;
import org.darkroomlibrary.service.ReservationWorkflowService;
import org.darkroomlibrary.mapper.BookReservationMapper;
import org.darkroomlibrary.mapper.BorrowRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class BookReservationServiceImplTest extends BaseTest {

    @Resource
    private BookReservationService bookReservationService;
    @Resource
    private ReservationWorkflowService reservationWorkflowService;
    @Resource
    private BookReservationMapper bookReservationMapper;
    @Resource
    private BorrowRecordMapper borrowRecordMapper;

    @BeforeEach
    void setUp() {
        clearContext();
    }

    @Test
    @DisplayName("Reserve - duplicate active reservation is rejected")
    void testDuplicateActiveReservationRejected() {
        User user = createTestUser("reserveunique", "Reserve Unique", "reserveunique@test.com");
        Book book = createTestBook("reserve-unique-book", "reserve-author", 0);
        setCurrentUser(user.getId(), user.getUserRole());

        ApiResponse<Void> first = bookReservationService.reserve(book.getId());
        ApiResponse<Void> duplicate = bookReservationService.reserve(book.getId());

        assertNotNull(first);
        assertNotNull(duplicate);
        assertEquals(200, first.getCode());
        assertEquals(400, duplicate.getCode());
    }

    @Test
    @DisplayName("Reserve - active borrower cannot reserve the same book")
    void testActiveBorrowerCannotReserveSameBook() {
        User user = createTestUser("reserveborrowed", "Reserve Borrowed", "reserveborrowed@test.com");
        Book book = createTestBook("reserve-borrowed-book", "reserve-author", 0);
        borrowRecordMapper.insert(BorrowRecord.builder()
                .userId(user.getId())
                .bookId(book.getId())
                .borrowTime(LocalDateTime.now().minusDays(1))
                .dueDate(LocalDateTime.now().plusDays(29))
                .status(false)
                .renewCount(0)
                .build());
        setCurrentUser(user.getId(), user.getUserRole());

        ApiResponse<Void> result = bookReservationService.reserve(book.getId());

        assertEquals(400, result.getCode());
        assertEquals(0, bookReservationMapper.countActiveByUserId(user.getId()));
    }

    @Test
    @DisplayName("过期预约释放后自动通知下一位")
    void testExpiredNotificationAdvancesQueue() {
        User first = createTestUser("reserve_first", "第一预约人", "reserve_first@test.com");
        User second = createTestUser("reserve_second", "第二预约人", "reserve_second@test.com");
        Book book = createTestBook("预约递补图书", "预约作者", 1);
        BookReservation expired = BookReservation.builder()
                .userId(first.getId())
                .bookId(book.getId())
                .reserveTime(LocalDateTime.now().minusDays(3))
                .status(3)
                .notifyTime(LocalDateTime.now().minusHours(49))
                .build();
        BookReservation waiting = BookReservation.builder()
                .userId(second.getId())
                .bookId(book.getId())
                .reserveTime(LocalDateTime.now().minusDays(1))
                .status(0)
                .build();
        bookReservationMapper.insert(expired);
        bookReservationMapper.insert(waiting);

        reservationWorkflowService.expireOverdueNotifications();

        assertEquals(4, bookReservationMapper.getById(expired.getId()).getStatus());
        assertEquals(3, bookReservationMapper.getById(waiting.getId()).getStatus());
        assertNotNull(bookReservationMapper.getById(waiting.getId()).getNotifyTime());
    }
}
