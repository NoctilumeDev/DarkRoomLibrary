package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.domain.model.BookReservation;
import org.darkroomlibrary.domain.model.BorrowRecord;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.service.BookReservationService;
import org.darkroomlibrary.service.BorrowRecordService;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class BookReservationServiceImplTest extends BaseTest {

    @Resource
    private BookReservationService bookReservationService;
    @Resource
    private ReservationWorkflowService reservationWorkflowService;
    @Resource
    private ReservationWorkflowServiceImpl reservationWorkflowServiceImpl;
    @Resource
    private BookReservationMapper bookReservationMapper;
    @Resource
    private BorrowRecordMapper borrowRecordMapper;
    @Resource
    private BorrowRecordService borrowRecordService;

    @BeforeEach
    void setUp() {
        clearContext();
    }

    @Test
    @DisplayName("Reserve - duplicate active reservation is rejected")
    void testDuplicateActiveReservationRejected() {
        User user = createTestUser("reserveunique", "Reserve Unique", "reserveunique@example.test");
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
        User user = createTestUser("reserveborrowed", "Reserve Borrowed", "reserveborrowed@example.test");
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
        User first = createTestUser("reserve_first", "第一预约人", "reserve_first@example.test");
        User second = createTestUser("reserve_second", "第二预约人", "reserve_second@example.test");
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

    @Test
    @DisplayName("预约通知人数不超过实际可借库存")
    void testNotificationCapacityMatchesAvailableStock() {
        User first = createTestUser("reserve_capacity_first", "容量预约人一", "reserve_capacity_first@example.test");
        User second = createTestUser("reserve_capacity_second", "容量预约人二", "reserve_capacity_second@example.test");
        Book book = createTestBook("预约容量图书", "预约作者", 1);
        BookReservation firstWaiting = BookReservation.builder()
                .userId(first.getId())
                .bookId(book.getId())
                .reserveTime(LocalDateTime.now().minusMinutes(2))
                .status(0)
                .build();
        BookReservation secondWaiting = BookReservation.builder()
                .userId(second.getId())
                .bookId(book.getId())
                .reserveTime(LocalDateTime.now().minusMinutes(1))
                .status(0)
                .build();
        bookReservationMapper.insert(firstWaiting);
        bookReservationMapper.insert(secondWaiting);

        reservationWorkflowService.onBookReturned(book.getId());
        reservationWorkflowService.onBookReturned(book.getId());

        assertEquals(3, bookReservationMapper.getById(firstWaiting.getId()).getStatus());
        assertEquals(0, bookReservationMapper.getById(secondWaiting.getId()).getStatus());
        assertEquals(1, bookReservationMapper.countNotifiedByBookId(book.getId()));
    }

    @Test
    @DisplayName("一次库存事件填满全部可借预约名额")
    void testSingleInventoryEventFillsAllAvailableCapacity() {
        User first = createTestUser("reserve_fill_first", "填充预约人一", "reserve_fill_first@example.test");
        User second = createTestUser("reserve_fill_second", "填充预约人二", "reserve_fill_second@example.test");
        Book book = createTestBook("预约批量通知图书", "预约作者", 2);
        bookReservationMapper.insert(BookReservation.builder()
                .userId(first.getId())
                .bookId(book.getId())
                .reserveTime(LocalDateTime.now().minusMinutes(2))
                .status(0)
                .build());
        bookReservationMapper.insert(BookReservation.builder()
                .userId(second.getId())
                .bookId(book.getId())
                .reserveTime(LocalDateTime.now().minusMinutes(1))
                .status(0)
                .build());

        reservationWorkflowService.onBookReturned(book.getId());

        assertEquals(2, bookReservationMapper.countNotifiedByBookId(book.getId()));
    }

    @Test
    @DisplayName("数据库对账补偿遗漏的库存通知事件")
    void testReconciliationRecoversMissedInventoryEvent() {
        User user = createTestUser("reserve_reconcile", "对账预约人", "reserve_reconcile@example.test");
        Book book = createTestBook("预约对账图书", "预约作者", 1);
        BookReservation waiting = BookReservation.builder()
                .userId(user.getId())
                .bookId(book.getId())
                .reserveTime(LocalDateTime.now().minusMinutes(1))
                .status(0)
                .build();
        bookReservationMapper.insert(waiting);

        reservationWorkflowServiceImpl.reconcileAvailableReservations();

        assertEquals(3, bookReservationMapper.getById(waiting.getId()).getStatus());
    }

    @Test
    @DisplayName("预约过期与借阅并发时不会死锁且库存保持一致")
    void testExpirationAndBorrowUseConsistentLockOrder() throws Exception {
        User user = createTestUser("reserve_expire_borrow", "并发预约人", "reserve_expire_borrow@example.test");
        Book book = createTestBook("预约过期并发图书", "预约作者", 1);
        BookReservation reservation = BookReservation.builder()
                .userId(user.getId())
                .bookId(book.getId())
                .reserveTime(LocalDateTime.now().minusDays(3))
                .status(3)
                .notifyTime(LocalDateTime.now().minusHours(49))
                .build();
        bookReservationMapper.insert(reservation);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<ApiResponse<Void>> borrow = executor.submit(() -> {
            start.await();
            setCurrentUser(user.getId(), user.getUserRole());
            try {
                return borrowRecordService.borrow(book.getId());
            } finally {
                clearContext();
            }
        });
        Future<?> expiration = executor.submit(() -> {
            start.await();
            reservationWorkflowService.expireOverdueNotifications();
            return null;
        });

        start.countDown();
        ApiResponse<Void> borrowResult = borrow.get(5, TimeUnit.SECONDS);
        expiration.get(5, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(200, borrowResult.getCode());
        assertEquals(0, bookMapper.getById(book.getId()).getAvailableCount());
        assertEquals(1, borrowRecordMapper.countActiveByUserIdAndBookId(user.getId(), book.getId()));
        assertTrue(List.of(1, 4).contains(bookReservationMapper.getById(reservation.getId()).getStatus()));
    }
}
