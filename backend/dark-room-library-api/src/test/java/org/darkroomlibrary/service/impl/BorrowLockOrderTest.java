package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.domain.model.BorrowRecord;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.domain.type.AccountStatus;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.infrastructure.event.DomainEventPublisher;
import org.darkroomlibrary.mapper.BookMapper;
import org.darkroomlibrary.mapper.BookReservationMapper;
import org.darkroomlibrary.mapper.BorrowRecordMapper;
import org.darkroomlibrary.mapper.UserMapper;
import org.darkroomlibrary.service.FineService;
import org.darkroomlibrary.service.ReservationWorkflowService;
import org.darkroomlibrary.service.support.RecommendationSourceVersionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BorrowLockOrderTest {

    private static final int USER_ID = 7;
    private static final int BOOK_ID = 11;
    private static final int RECORD_ID = 13;

    @Mock
    private BorrowRecordMapper borrowRecordMapper;
    @Mock
    private BookMapper bookMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private FineService fineService;
    @Mock
    private BookReservationMapper bookReservationMapper;
    @Mock
    private DomainEventPublisher domainEventPublisher;
    @Mock
    private ReservationWorkflowService reservationWorkflowService;
    @Mock
    private RecommendationSourceVersionService recommendationSourceVersionService;

    private BorrowRecordServiceImpl service;
    private BorrowRecord activeRecord;

    @BeforeEach
    void setUp() {
        service = new BorrowRecordServiceImpl();
        ReflectionTestUtils.setField(service, "borrowRecordMapper", borrowRecordMapper);
        ReflectionTestUtils.setField(service, "bookMapper", bookMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "fineService", fineService);
        ReflectionTestUtils.setField(service, "bookReservationMapper", bookReservationMapper);
        ReflectionTestUtils.setField(service, "domainEventPublisher", domainEventPublisher);
        ReflectionTestUtils.setField(service, "reservationWorkflowService", reservationWorkflowService);
        ReflectionTestUtils.setField(service, "recommendationSourceVersionService",
                recommendationSourceVersionService);
        ReflectionTestUtils.setField(service, "renewWindowDaysBeforeDue", 3);
        ReflectionTestUtils.setField(service, "maxRenewCount", 1);
        ReflectionTestUtils.setField(service, "renewExtendDays", 30);
        ReflectionTestUtils.setField(service, "bookReturnedRoutingKey", "book.returned");

        activeRecord = BorrowRecord.builder()
                .id(RECORD_ID)
                .userId(USER_ID)
                .bookId(BOOK_ID)
                .dueDate(LocalDateTime.now().plusDays(2))
                .status(false)
                .renewCount(0)
                .build();
        CurrentUserContext.bind(USER_ID, UserRole.READER.code());
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void returnLocksUserThenBookThenBorrowRecord() {
        stubLockContext();
        when(fineService.calculateFine(eq(activeRecord.getDueDate()), any(LocalDateTime.class)))
                .thenReturn(BigDecimal.ZERO);
        when(borrowRecordMapper.updateReturnStatus(
                eq(RECORD_ID), any(LocalDateTime.class), eq(BigDecimal.ZERO))).thenReturn(1);
        when(bookMapper.increaseAvailableCount(BOOK_ID)).thenReturn(1);
        when(domainEventPublisher.publish("book.returned", BOOK_ID)).thenReturn(true);

        assertEquals(200, service.returnBook(RECORD_ID).getCode());

        InOrder order = inOrder(userMapper, bookMapper, borrowRecordMapper);
        order.verify(userMapper).findByIdForUpdate(USER_ID);
        order.verify(bookMapper).findByIdForUpdate(BOOK_ID);
        order.verify(borrowRecordMapper).findByIdForUpdate(RECORD_ID);
        verify(recommendationSourceVersionService).invalidateUserAndGlobalAfterCommit(USER_ID);
    }

    @Test
    void renewLocksUserThenBookThenBorrowRecord() {
        stubLockContext();
        when(bookReservationMapper.countActiveByBookId(BOOK_ID)).thenReturn(0);
        when(borrowRecordMapper.updateRenewStatus(
                eq(RECORD_ID),
                any(LocalDateTime.class),
                eq(1),
                eq(activeRecord.getDueDate()),
                eq(0))).thenReturn(1);

        assertEquals(200, service.renew(RECORD_ID).getCode());

        InOrder order = inOrder(userMapper, bookMapper, borrowRecordMapper);
        order.verify(userMapper).findByIdForUpdate(USER_ID);
        order.verify(bookMapper).findByIdForUpdate(BOOK_ID);
        order.verify(borrowRecordMapper).findByIdForUpdate(RECORD_ID);
    }

    @Test
    void borrowRejectsAFrozenAccountBeforeReadingBookState() {
        when(userMapper.findByIdForUpdate(USER_ID)).thenReturn(User.builder()
                .id(USER_ID)
                .userRole(UserRole.READER.code())
                .accountStatus(AccountStatus.FROZEN.code())
                .isLogin(false)
                .build());

        var response = service.borrow(BOOK_ID);

        assertEquals(400, response.getCode());
        assertEquals("当前账号状态不允许借阅", response.getMsg());
    }

    private void stubLockContext() {
        when(borrowRecordMapper.getById(RECORD_ID)).thenReturn(activeRecord);
        when(userMapper.findByIdForUpdate(USER_ID)).thenReturn(User.builder()
                .id(USER_ID)
                .userRole(UserRole.READER.code())
                .accountStatus(AccountStatus.NORMAL.code())
                .isLogin(false)
                .build());
        when(bookMapper.findByIdForUpdate(BOOK_ID)).thenReturn(Book.builder()
                .id(BOOK_ID)
                .isDeleted(false)
                .build());
        when(borrowRecordMapper.findByIdForUpdate(RECORD_ID)).thenReturn(activeRecord);
    }
}
