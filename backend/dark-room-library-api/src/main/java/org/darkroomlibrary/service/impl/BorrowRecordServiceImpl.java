package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.infrastructure.event.DomainEventPublisher;
import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.mapper.BookMapper;
import org.darkroomlibrary.mapper.BookReservationMapper;
import org.darkroomlibrary.mapper.BorrowRecordMapper;
import org.darkroomlibrary.mapper.UserMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.response.PageResponse;
import org.darkroomlibrary.web.dto.query.BorrowRecordPageQuery;
import org.darkroomlibrary.domain.type.AccountStatus;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.domain.model.BookReservation;
import org.darkroomlibrary.domain.model.BorrowRecord;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.web.view.BorrowRecordView;
import org.darkroomlibrary.service.BorrowRecordService;
import org.darkroomlibrary.service.FineService;
import org.darkroomlibrary.service.ReservationWorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class BorrowRecordServiceImpl implements BorrowRecordService {

    @Resource
    private BorrowRecordMapper borrowRecordMapper;

    @Resource
    private BookMapper bookMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private FineService fineService;

    @Resource
    private BookReservationMapper bookReservationMapper;

    @Resource
    private DomainEventPublisher domainEventPublisher;

    @Resource
    private ReservationWorkflowService reservationWorkflowService;

    @Value("${borrow.max-count:5}")
    private int maxBorrowCount;

    @Value("${borrow.renew.max-count:1}")
    private int maxRenewCount;

    @Value("${borrow.renew.window-days-before-due:3}")
    private int renewWindowDaysBeforeDue;

    @Value("${borrow.renew.extend-days:30}")
    private int renewExtendDays;

    @Value("${middleware.rabbit.book-returned-routing-key:book.returned}")
    private String bookReturnedRoutingKey;

    @Override
    @Transactional
    public ApiResponse<Void> borrow(Integer bookId) {
        Integer userId = CurrentUserContext.userId();
        if (userId == null) {
            return ApiResponse.error("身份认证失败，请先登录");
        }
        if (bookId == null) {
            return ApiResponse.error("图书编号不能为空");
        }
        User user = userMapper.findByIdForUpdate(userId);
        if (user == null) {
            return ApiResponse.error("当前用户不存在或已被删除");
        }
        if (!Objects.equals(user.getAccountStatus(), AccountStatus.NORMAL.code())
                || Boolean.TRUE.equals(user.getIsLogin())) {
            return ApiResponse.error("当前账号状态不允许借阅");
        }
        int activeCount = borrowRecordMapper.getActiveCountByUserId(userId);
        if (activeCount >= maxBorrowCount) {
            return ApiResponse.error("借阅数量已达上限（" + maxBorrowCount + "本），请先归还部分图书");
        }
        Book book = bookMapper.findByIdForUpdate(bookId);
        if (book == null || Boolean.TRUE.equals(book.getIsDeleted())) {
            return ApiResponse.error("图书不存在");
        }
        if (borrowRecordMapper.countActiveByUserIdAndBookId(userId, bookId) > 0) {
            return ApiResponse.error("您已借阅该图书，请勿重复借阅");
        }
        BookReservation notifiedReservation = bookReservationMapper.findNotifiedByBookIdAndUserId(bookId, userId);
        if (bookReservationMapper.countActiveByBookId(bookId) > 0 && notifiedReservation == null) {
            return ApiResponse.error("该图书已有预约队列，请等待预约通知");
        }
        int affectedRows = bookMapper.decreaseAvailableCount(bookId);
        if (affectedRows == 0) {
            return ApiResponse.error("图书库存不足，无法借阅");
        }
        // 条件插入（DB层原子防并发重复借阅）
        LocalDateTime now = LocalDateTime.now();
        BorrowRecord record = BorrowRecord.builder()
                .userId(userId)
                .bookId(bookId)
                .borrowTime(now)
                .dueDate(now.plusDays(30))
                .status(false)
                .fineAmount(BigDecimal.ZERO)
                .renewCount(0)
                .build();
        int inserted = borrowRecordMapper.insertIfNoActiveBorrow(record);
        if (inserted == 0) {
            // 并发冲突：恢复库存
            bookMapper.increaseAvailableCount(bookId);
            return ApiResponse.error("您已借阅该图书，请勿重复借阅");
        }
        if (notifiedReservation != null && bookReservationMapper.markBorrowed(notifiedReservation.getId()) == 0) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error("预约状态已变化，请刷新后重试");
        }
        return ApiResponse.success("借阅成功");
    }

    @Override
    @Transactional
    public ApiResponse<Void> returnBook(Integer recordId) {
        Integer userId = CurrentUserContext.userId();
        BorrowRecord record = borrowRecordMapper.findByIdForUpdate(recordId);
        if (record == null) {
            return ApiResponse.error("借阅记录不存在");
        }
        if (!CurrentUserContext.isAdministrator() && !record.getUserId().equals(userId)) {
            return ApiResponse.error("该借阅记录不属于当前用户，无法归还");
        }
        if (Boolean.TRUE.equals(record.getStatus())) {
            return ApiResponse.error("该图书已归还，请勿重复操作");
        }
        LocalDateTime now = LocalDateTime.now();
        BigDecimal fine = fineService.calculateFine(record.getDueDate(), now);
        // DB条件更新（WHERE status=0 防并发重复归还）
        int affected = borrowRecordMapper.updateReturnStatus(recordId, now, fine);
        if (affected == 0) {
            return ApiResponse.error("该图书已归还，请勿重复操作");
        }
        // 原子增加库存
        if (bookMapper.increaseAvailableCount(record.getBookId()) == 0) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResponse.error("图书库存状态异常，归还失败");
        }
        publishBookReturnedAfterCommit(record.getBookId());
        if (fine.compareTo(BigDecimal.ZERO) > 0) {
            return ApiResponse.success("归还成功，逾期罚款：" + fine + "元");
        }
        return ApiResponse.success("归还成功");
    }

    @Override
    @Transactional
    public ApiResponse<Void> renew(Integer recordId) {
        Integer userId = CurrentUserContext.userId();
        BorrowRecord record = borrowRecordMapper.findByIdForUpdate(recordId);
        if (record == null) {
            return ApiResponse.error("借阅记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            return ApiResponse.error("该借阅记录不属于当前用户，无法续借");
        }
        if (Boolean.TRUE.equals(record.getStatus())) {
            return ApiResponse.error("该图书已归还，无法续借");
        }
        LocalDateTime now = LocalDateTime.now();
        if (record.getDueDate() == null || now.isAfter(record.getDueDate())) {
            return ApiResponse.error("图书已逾期，无法续借");
        }
        if (now.isBefore(record.getDueDate().minusDays(renewWindowDaysBeforeDue))) {
            return ApiResponse.error("仅允许在到期前" + renewWindowDaysBeforeDue + "天内续借");
        }
        int renewCount = record.getRenewCount() == null ? 0 : record.getRenewCount();
        if (renewCount >= maxRenewCount) {
            return ApiResponse.error("该借阅记录已达到续借次数上限");
        }
        Book book = bookMapper.findByIdForUpdate(record.getBookId());
        if (book == null || Boolean.TRUE.equals(book.getIsDeleted())) {
            return ApiResponse.error("图书不存在，无法续借");
        }
        if (bookReservationMapper.countActiveByBookId(record.getBookId()) > 0) {
            return ApiResponse.error("该图书存在预约排队，暂不允许续借");
        }
        LocalDateTime newDueDate = record.getDueDate().plusDays(renewExtendDays);
        int affected = borrowRecordMapper.updateRenewStatus(
                recordId, newDueDate, renewCount + 1, record.getDueDate(), renewCount);
        if (affected == 0) {
            return ApiResponse.error("续借失败，请刷新后重试");
        }
        return ApiResponse.success("续借成功，应还日期已延长至：" + newDueDate);
    }

    @Override
    public ApiResponse<List<BorrowRecordView>> query(BorrowRecordPageQuery dto) {
        if (dto == null) {
            dto = new BorrowRecordPageQuery();
        }
        if (!CurrentUserContext.isAdministrator()) {
            dto.setUserId(CurrentUserContext.userId());
        }
        List<BorrowRecordView> recordList = borrowRecordMapper.query(dto);
        Integer totalCount = borrowRecordMapper.queryCount(dto);
        return PageResponse.success(recordList, totalCount);
    }

    private void publishBookReturnedAfterCommit(Integer bookId) {
        Runnable publishTask = () -> {
            try {
                if (!domainEventPublisher.publish(bookReturnedRoutingKey, bookId)) {
                    reservationWorkflowService.onBookReturned(bookId);
                }
            } catch (Exception e) {
                log.warn("BookReturnedEvent fallback failed: bookId={}, error={}", bookId, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishTask.run();
                }
            });
            return;
        }
        publishTask.run();
    }
}
