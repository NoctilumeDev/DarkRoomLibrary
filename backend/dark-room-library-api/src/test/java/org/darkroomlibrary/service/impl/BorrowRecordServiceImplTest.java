package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.mapper.BookReservationMapper;
import org.darkroomlibrary.mapper.NotificationTaskMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.domain.model.BookReservation;
import org.darkroomlibrary.domain.model.BorrowRecord;
import org.darkroomlibrary.domain.model.NotificationTask;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.web.view.BorrowRecordView;
import org.darkroomlibrary.web.dto.query.BorrowRecordPageQuery;
import org.darkroomlibrary.service.BorrowRecordService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.annotation.DirtiesContext;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 借阅记录服务测试
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BorrowRecordServiceImplTest extends BaseTest {

    @Resource
    private BorrowRecordService borrowRecordService;

    @Resource
    private BookReservationMapper bookReservationMapper;

    @Resource
    private NotificationTaskMapper notificationTaskMapper;

    @Resource
    private BorrowReminderService borrowReminderService;

    private User testUser;
    private Book testBook;

    @BeforeEach
    void setUp() {
        clearContext();
        String suffix = String.valueOf(System.nanoTime());
        testUser = createTestUser(
                "borrowtest" + suffix,
                "借阅测试用户" + suffix,
                "borrow" + suffix + "@example.test");
        testBook = createTestBook("测试图书-借阅-" + suffix, "测试作者", 3);
        setCurrentUser(testUser.getId(), testUser.getUserRole());
    }

    @Test
    @Order(1)
    @DisplayName("借阅成功 - 正常借阅")
    void testBorrowSuccess() {
        ApiResponse<Void> result = borrowRecordService.borrow(testBook.getId());
        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    @Order(2)
    @DisplayName("借阅失败 - 图书不存在")
    void testBorrowBookNotExist() {
        ApiResponse<Void> result = borrowRecordService.borrow(99999);
        assertNotNull(result);
        assertEquals(400, result.getCode());
    }

    @Test
    @Order(3)
    @DisplayName("借阅失败 - 库存不足")
    void testBorrowOutOfStock() {
        Book zeroStock = createTestBook("零库存图书", "零库存作者", 0);
        // 手动设置可借数量为0
        Book update = Book.builder().id(zeroStock.getId()).availableCount(0).build();
        // 注意：需要 bookMapper 支持仅更新 availableCount，这里只验证逻辑
        ApiResponse<Void> result = borrowRecordService.borrow(zeroStock.getId());
        assertNotNull(result);
        assertEquals(400, result.getCode());
    }

    @Test
    @Order(4)
    @DisplayName("借阅失败 - 重复借阅")
    void testBorrowDuplicate() {
        // 先借一次
        borrowRecordService.borrow(testBook.getId());
        // 再借一次，应该被拒绝
        ApiResponse<Void> result = borrowRecordService.borrow(testBook.getId());
        assertNotNull(result);
        assertEquals(400, result.getCode());
    }

    @Test
    @Order(5)
    @DisplayName("借阅失败 - 超过借阅上限")
    void testBorrowExceedLimit() {
        // 创建5本不同的书，全部借出
        for (int i = 0; i < 5; i++) {
            Book b = createTestBook("上限测试图书" + i, "作者" + i, 5);
            borrowRecordService.borrow(b.getId());
        }
        // 第6本应该被拒绝
        Book extraBook = createTestBook("额外图书", "额外作者", 5);
        ApiResponse<Void> result = borrowRecordService.borrow(extraBook.getId());
        assertNotNull(result);
        assertEquals(400, result.getCode());
    }

    @Test
    @Order(6)
    @DisplayName("还书成功 - 按时归还")
    void testReturnBookSuccess() {
        Book returnBook = createTestBook("归还测试图书", "归还作者", 5);
        User returnUser = createTestUser("returnuser", "归还用户", "return@example.test");
        setCurrentUser(returnUser.getId(), returnUser.getUserRole());

        // 先借书
        borrowRecordService.borrow(returnBook.getId());

        // 查询借阅记录ID
        BorrowRecordPageQuery queryDto = new BorrowRecordPageQuery();
        queryDto.setUserId(returnUser.getId());
        queryDto.setBookId(returnBook.getId());
        queryDto.setCurrent(0);
        queryDto.setSize(10);
        List<BorrowRecordView> records = borrowRecordService.query(queryDto).getData();
        if (records != null && !records.isEmpty()) {
            BorrowRecordView record = records.get(0);
            ApiResponse<Void> result = borrowRecordService.returnBook(record.getId());
            assertNotNull(result);
            assertEquals(200, result.getCode());
        }
    }

    @Test
    @Order(7)
    @DisplayName("还书成功 - 逾期归还（有罚款）")
    void testReturnBookOverdue() {
        Book overdueBook = createTestBook("逾期测试图书", "逾期作者", 5);
        User overdueUser = createTestUser("overdueuser", "逾期用户", "overdue@example.test");
        setCurrentUser(overdueUser.getId(), overdueUser.getUserRole());

        // 创建一条逾期借阅记录（应还日期在过去）
        BorrowRecord record = createTestBorrowRecord(
                overdueUser.getId(),
                overdueBook.getId(),
                LocalDateTime.now().minusDays(5)
        );

        ApiResponse<Void> result = borrowRecordService.returnBook(record.getId());
        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    @Order(8)
    @DisplayName("还书失败 - 记录不存在")
    void testReturnBookNotExist() {
        ApiResponse<Void> result = borrowRecordService.returnBook(99999);
        assertNotNull(result);
        assertEquals(400, result.getCode());
    }

    @Test
    @Order(9)
    @DisplayName("还书失败 - 非本人记录")
    void testReturnBookNotOwn() {
        User otherUser = createTestUser("otheruser", "其他用户", "other@example.test");
        Book otherBook = createTestBook("其他图书", "其他作者", 5);
        setCurrentUser(otherUser.getId(), otherUser.getUserRole());
        borrowRecordService.borrow(otherBook.getId());
        BorrowRecordPageQuery ownedQuery = new BorrowRecordPageQuery();
        ownedQuery.setUserId(otherUser.getId());
        ownedQuery.setCurrent(0);
        ownedQuery.setSize(10);
        List<BorrowRecordView> ownedRecords = borrowRecordService.query(ownedQuery).getData();
        assertNotNull(ownedRecords);
        assertFalse(ownedRecords.isEmpty());
        Integer recordId = ownedRecords.get(0).getId();

        // 切换到另一个用户
        User anotherUser = createTestUser("anotheruser", "另一个用户", "another@example.test");
        setCurrentUser(anotherUser.getId(), anotherUser.getUserRole());

        ApiResponse<Void> result = borrowRecordService.returnBook(recordId);
        assertNotNull(result);
        assertEquals(400, result.getCode());
    }

    @Test
    @Order(10)
    @DisplayName("还书成功 - 管理员可代用户归还")
    void testAdminCanReturnOtherUserRecord() {
        User owner = createTestUser("adminreturnowner", "代还用户", "adminreturnowner@example.test");
        Book book = createTestBook("管理员代还测试图书", "管理员代还作者", 5);
        setCurrentUser(owner.getId(), owner.getUserRole());
        borrowRecordService.borrow(book.getId());

        BorrowRecordPageQuery queryDto = new BorrowRecordPageQuery();
        queryDto.setUserId(owner.getId());
        queryDto.setBookId(book.getId());
        queryDto.setCurrent(0);
        queryDto.setSize(10);
        List<BorrowRecordView> records = borrowRecordService.query(queryDto).getData();
        assertNotNull(records);
        assertFalse(records.isEmpty());

        User admin = createTestUser("adminreturner", "管理员代还", "adminreturner@example.test");
        setCurrentUser(admin.getId(), UserRole.ADMIN.code());

        ApiResponse<Void> result = borrowRecordService.returnBook(records.get(0).getId());
        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    @Order(11)
    @DisplayName("查询隔离 - 普通用户不能按 userId 查询他人借阅记录")
    void testReaderCannotQueryOtherUserRecords() {
        User owner = createTestUser("borrowowner", "借阅记录拥有者", "borrowowner@example.test");
        Book ownerBook = createTestBook("隔离测试图书", "隔离作者", 5);
        setCurrentUser(owner.getId(), owner.getUserRole());
        borrowRecordService.borrow(ownerBook.getId());

        User reader = createTestUser("borrowreader", "借阅查询用户", "borrowreader@example.test");
        setCurrentUser(reader.getId(), reader.getUserRole());

        BorrowRecordPageQuery queryDto = new BorrowRecordPageQuery();
        queryDto.setUserId(owner.getId());
        queryDto.setCurrent(0);
        queryDto.setSize(10);
        List<BorrowRecordView> records = borrowRecordService.query(queryDto).getData();
        assertTrue(records == null || records.isEmpty());
    }

    @Test
    @Order(12)
    @DisplayName("Concurrent borrow - single stock only allows one success")
    void testConcurrentBorrowSingleStock() throws Exception {
        Book singleCopyBook = createTestBook("concurrent-borrow-book", "concurrent-author", 1);
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            users.add(createTestUser("concurrent_borrow_" + i, "Concurrent User " + i, "concurrent" + i + "@example.test"));
        }

        ExecutorService executor = Executors.newFixedThreadPool(users.size());
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (User user : users) {
            futures.add(executor.submit(() -> {
                start.await();
                setCurrentUser(user.getId(), user.getUserRole());
                try {
                    ApiResponse<Void> result = borrowRecordService.borrow(singleCopyBook.getId());
                    if (result != null && Integer.valueOf(200).equals(result.getCode())) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    clearContext();
                }
                return null;
            }));
        }

        start.countDown();
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        Book reloaded = bookMapper.selectById(singleCopyBook.getId());
        List<BorrowRecord> activeRecords = borrowRecordMapper.selectList(
                new QueryWrapper<BorrowRecord>()
                        .eq("book_id", singleCopyBook.getId())
                        .eq("status", false)
        );
        assertEquals(1, successCount.get());
        assertEquals(1, activeRecords.size());
        assertEquals(0, reloaded.getAvailableCount());
    }

    @Test
    @Order(13)
    @DisplayName("Renew - allowed once inside due-date window")
    void testRenewSuccessInsideWindow() {
        User renewUser = createTestUser("renewuser", "Renew User", "renew@example.test");
        Book renewBook = createTestBook("renew-book", "renew-author", 2);
        LocalDateTime dueDate = LocalDateTime.now().plusDays(2);
        BorrowRecord record = createTestBorrowRecord(renewUser.getId(), renewBook.getId(), dueDate);
        setCurrentUser(renewUser.getId(), renewUser.getUserRole());

        ApiResponse<Void> result = borrowRecordService.renew(record.getId());

        BorrowRecord updated = borrowRecordMapper.selectById(record.getId());
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(1, updated.getRenewCount());
        assertTrue(updated.getDueDate().isAfter(dueDate.plusDays(29)));
    }

    @Test
    @Order(14)
    @DisplayName("Renew - blocked when reservation queue exists")
    void testRenewBlockedWhenReservationExists() {
        User renewUser = createTestUser("renewblocked", "Renew Blocked", "renewblocked@example.test");
        User reserveUser = createTestUser("renewreserve", "Renew Reserve", "renewreserve@example.test");
        Book renewBook = createTestBook("renew-block-book", "renew-block-author", 2);
        BorrowRecord record = createTestBorrowRecord(renewUser.getId(), renewBook.getId(), LocalDateTime.now().plusDays(2));
        bookReservationMapper.insert(BookReservation.builder()
                .userId(reserveUser.getId())
                .bookId(renewBook.getId())
                .reserveTime(LocalDateTime.now().minusMinutes(1))
                .status(0)
                .build());
        setCurrentUser(renewUser.getId(), renewUser.getUserRole());

        ApiResponse<Void> result = borrowRecordService.renew(record.getId());

        BorrowRecord updated = borrowRecordMapper.selectById(record.getId());
        assertNotNull(result);
        assertEquals(400, result.getCode());
        assertTrue(updated.getRenewCount() == null || updated.getRenewCount() == 0);
    }

    @Test
    @Order(15)
    @DisplayName("Return book - notifies earliest reservation without RabbitMQ")
    void testReturnBookNotifiesReservationWithMqFallback() {
        User borrower = createTestUser("returnfallback", "Return Fallback", "returnfallback@example.test");
        User reserveUser = createTestUser("reservefallback", "Reserve Fallback", "reservefallback@example.test");
        Book reservedBook = createTestBook("reservation-fallback-book", "reservation-fallback-author", 1);
        setCurrentUser(borrower.getId(), borrower.getUserRole());
        assertEquals(200, borrowRecordService.borrow(reservedBook.getId()).getCode());

        BookReservation reservation = BookReservation.builder()
                .userId(reserveUser.getId())
                .bookId(reservedBook.getId())
                .reserveTime(LocalDateTime.now().minusMinutes(5))
                .status(0)
                .build();
        bookReservationMapper.insert(reservation);

        BorrowRecord record = borrowRecordMapper.selectOne(
                new QueryWrapper<BorrowRecord>()
                        .eq("user_id", borrower.getId())
                        .eq("book_id", reservedBook.getId())
                        .eq("status", false)
        );
        ApiResponse<Void> result = borrowRecordService.returnBook(record.getId());

        BookReservation updated = bookReservationMapper.selectById(reservation.getId());
        List<NotificationTask> tasks = notificationTaskMapper.selectList(
                new QueryWrapper<NotificationTask>()
                        .eq("receiver_email", reserveUser.getUserEmail())
        );
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(3, updated.getStatus());
        assertNotNull(updated.getNotifyTime());
        assertFalse(tasks.isEmpty());
        assertEquals(0, tasks.get(0).getStatus());
    }

    @Test
    @Order(16)
    @DisplayName("Borrow - reservation queue blocks others and marks notified reservation borrowed")
    void testBorrowRespectsNotifiedReservationQueue() {
        User notifiedUser = createTestUser("notifiedborrower", "Notified Borrower", "notifiedborrower@example.test");
        User otherUser = createTestUser("queueintruder", "Queue Intruder", "queueintruder@example.test");
        Book reservedBook = createTestBook("reserved-borrow-book", "reserved-borrow-author", 1);
        BookReservation reservation = BookReservation.builder()
                .userId(notifiedUser.getId())
                .bookId(reservedBook.getId())
                .reserveTime(LocalDateTime.now().minusHours(1))
                .status(3)
                .notifyTime(LocalDateTime.now().minusMinutes(5))
                .build();
        bookReservationMapper.insert(reservation);

        setCurrentUser(otherUser.getId(), otherUser.getUserRole());
        ApiResponse<Void> blocked = borrowRecordService.borrow(reservedBook.getId());

        setCurrentUser(notifiedUser.getId(), notifiedUser.getUserRole());
        ApiResponse<Void> allowed = borrowRecordService.borrow(reservedBook.getId());

        BookReservation updatedReservation = bookReservationMapper.selectById(reservation.getId());
        Book updatedBook = bookMapper.selectById(reservedBook.getId());
        assertNotNull(blocked);
        assertEquals(400, blocked.getCode());
        assertNotNull(allowed);
        assertEquals(200, allowed.getCode());
        assertEquals(1, updatedReservation.getStatus());
        assertEquals(0, updatedBook.getAvailableCount());
    }

    @Test
    @Order(17)
    @DisplayName("Due reminder - creates one notification task and marks record reminded")
    void testDueReminderCreatesSingleNotificationTask() {
        String suffix = String.valueOf(System.nanoTime());
        User borrower = createTestUser("reminder" + suffix, "Reminder User", "reminder" + suffix + "@example.test");
        Book book = createTestBook("due-reminder-book-" + suffix, "reminder-author", 1);
        BorrowRecord record = createTestBorrowRecord(
                borrower.getId(),
                book.getId(),
                LocalDateTime.now().plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0)
        );

        borrowReminderService.sendDueReminders();
        borrowReminderService.sendDueReminders();

        BorrowRecord updated = borrowRecordMapper.selectById(record.getId());
        List<NotificationTask> tasks = notificationTaskMapper.selectList(
                new QueryWrapper<NotificationTask>()
                        .eq("receiver_email", borrower.getUserEmail())
                        .eq("subject", "【暗室藏书】还书提醒")
        );

        assertNotNull(updated.getDueReminderSentTime());
        assertEquals(1, tasks.size());
        assertEquals(0, tasks.get(0).getStatus());
    }

    @Test
    @Order(18)
    @DisplayName("Concurrent borrow - one user cannot exceed the active borrow limit")
    void testConcurrentBorrowDoesNotExceedUserLimit() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        User user = createTestUser(
                "borrow_limit_race_" + suffix,
                "Borrow Limit Race",
                "borrow-limit-race-" + suffix + "@example.test"
        );
        for (int i = 0; i < 4; i++) {
            Book activeBook = createTestBook("active-limit-book-" + suffix + "-" + i, "limit-author", 1);
            createTestBorrowRecord(user.getId(), activeBook.getId(), LocalDateTime.now().plusDays(30));
        }
        Book firstCandidate = createTestBook("limit-candidate-a-" + suffix, "limit-author", 1);
        Book secondCandidate = createTestBook("limit-candidate-b-" + suffix, "limit-author", 1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        List<Future<?>> futures = List.of(
                submitBorrow(executor, start, user, firstCandidate.getId(), successCount),
                submitBorrow(executor, start, user, secondCandidate.getId(), successCount)
        );

        start.countDown();
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        Book firstReloaded = bookMapper.selectById(firstCandidate.getId());
        Book secondReloaded = bookMapper.selectById(secondCandidate.getId());
        assertEquals(1, successCount.get());
        assertEquals(5, borrowRecordMapper.getActiveCountByUserId(user.getId()));
        assertEquals(1, firstReloaded.getAvailableCount() + secondReloaded.getAvailableCount());
    }

    @Test
    @Order(19)
    @DisplayName("Concurrent borrow - same user and book only create one active record")
    void testConcurrentBorrowSameUserAndBookOnlySucceedsOnce() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        User user = createTestUser(
                "borrow_duplicate_race_" + suffix,
                "Borrow Duplicate Race",
                "borrow-duplicate-race-" + suffix + "@example.test"
        );
        Book book = createTestBook("duplicate-race-book-" + suffix, "race-author", 2);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        List<Future<?>> futures = List.of(
                submitBorrow(executor, start, user, book.getId(), successCount),
                submitBorrow(executor, start, user, book.getId(), successCount)
        );

        start.countDown();
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        Book reloaded = bookMapper.selectById(book.getId());
        assertEquals(1, successCount.get());
        assertEquals(1, borrowRecordMapper.countActiveByUserIdAndBookId(user.getId(), book.getId()));
        assertEquals(1, reloaded.getAvailableCount());
    }

    @Test
    @Order(20)
    @DisplayName("Database invariant - duplicate active borrow is rejected")
    void testDatabaseRejectsDuplicateActiveBorrow() {
        String suffix = String.valueOf(System.nanoTime());
        User user = createTestUser(
                "borrow_unique_" + suffix,
                "Borrow Unique",
                "borrow-unique-" + suffix + "@example.test"
        );
        Book book = createTestBook("borrow-unique-book-" + suffix, "unique-author", 2);
        createTestBorrowRecord(user.getId(), book.getId(), LocalDateTime.now().plusDays(30));
        BorrowRecord duplicate = BorrowRecord.builder()
                .userId(user.getId())
                .bookId(book.getId())
                .borrowTime(LocalDateTime.now())
                .dueDate(LocalDateTime.now().plusDays(30))
                .status(false)
                .renewCount(0)
                .build();

        assertThrows(DuplicateKeyException.class, () -> borrowRecordMapper.insert(duplicate));
    }

    @Test
    @Order(21)
    @DisplayName("Concurrent renew - only one request consumes the renewal allowance")
    void testConcurrentRenewOnlySucceedsOnce() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        User user = createTestUser(
                "renew_race_" + suffix,
                "Renew Race",
                "renew-race-" + suffix + "@example.test"
        );
        Book book = createTestBook("renew-race-book-" + suffix, "renew-race-author", 1);
        LocalDateTime originalDueDate = LocalDateTime.now().plusDays(2);
        BorrowRecord record = createTestBorrowRecord(user.getId(), book.getId(), originalDueDate);
        LocalDateTime persistedDueDate = borrowRecordMapper.selectById(record.getId()).getDueDate();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        List<Future<?>> futures = List.of(
                submitRenew(executor, start, user, record.getId(), successCount),
                submitRenew(executor, start, user, record.getId(), successCount)
        );

        start.countDown();
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        BorrowRecord updated = borrowRecordMapper.selectById(record.getId());
        assertEquals(1, successCount.get());
        assertEquals(1, updated.getRenewCount());
        assertEquals(persistedDueDate.plusDays(30), updated.getDueDate());
    }

    private Future<?> submitBorrow(ExecutorService executor,
                                   CountDownLatch start,
                                   User user,
                                   Integer bookId,
                                   AtomicInteger successCount) {
        return executor.submit(() -> {
            start.await();
            setCurrentUser(user.getId(), user.getUserRole());
            try {
                ApiResponse<Void> result = borrowRecordService.borrow(bookId);
                if (result != null && Integer.valueOf(200).equals(result.getCode())) {
                    successCount.incrementAndGet();
                }
            } finally {
                clearContext();
            }
            return null;
        });
    }

    private Future<?> submitRenew(ExecutorService executor,
                                  CountDownLatch start,
                                  User user,
                                  Integer recordId,
                                  AtomicInteger successCount) {
        return executor.submit(() -> {
            start.await();
            setCurrentUser(user.getId(), user.getUserRole());
            try {
                ApiResponse<Void> result = borrowRecordService.renew(recordId);
                if (result != null && Integer.valueOf(200).equals(result.getCode())) {
                    successCount.incrementAndGet();
                }
            } finally {
                clearContext();
            }
            return null;
        });
    }
}
