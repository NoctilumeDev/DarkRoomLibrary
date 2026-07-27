package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.mapper.BookMapper;
import org.darkroomlibrary.mapper.BookReservationMapper;
import org.darkroomlibrary.mapper.BorrowRecordMapper;
import org.darkroomlibrary.mapper.UserMapper;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.response.PageResponse;
import org.darkroomlibrary.web.dto.query.BookReservationPageQuery;
import org.darkroomlibrary.domain.type.AccountStatus;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.domain.model.BookReservation;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.web.view.BookReservationView;
import org.darkroomlibrary.service.BookReservationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 图书预约服务实现
 */
@Service
public class BookReservationServiceImpl implements BookReservationService {

    @Resource
    private BookReservationMapper bookReservationMapper;

    @Resource
    private BookMapper bookMapper;

    @Resource
    private BorrowRecordMapper borrowRecordMapper;

    @Resource
    private UserMapper userMapper;

    @Value("${reservation.max-count:3}")
    private int maxReservationCount;

    @Override
    @Transactional
    public ApiResponse<Void> reserve(Integer bookId) {
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
        if (!java.util.Objects.equals(user.getAccountStatus(), AccountStatus.NORMAL.code())
                || Boolean.TRUE.equals(user.getIsLogin())
                || !java.util.Objects.equals(user.getUserRole(), CurrentUserContext.roleCode())
                || !java.util.Objects.equals(user.getUserRole(), UserRole.READER.code())) {
            return ApiResponse.error("当前账号状态不允许预约");
        }
        int activeCount = bookReservationMapper.countActiveByUserId(userId);
        if (activeCount >= maxReservationCount) {
            return ApiResponse.error("预约数量已达上限（" + maxReservationCount + "本），请先取消其他预约");
        }
        Book book = bookMapper.findByIdForUpdate(bookId);
        if (book == null || Boolean.TRUE.equals(book.getIsDeleted())) {
            return ApiResponse.error("图书不存在");
        }
        if (borrowRecordMapper.countActiveByUserIdAndBookId(userId, bookId) > 0) {
            return ApiResponse.error("您正在借阅该图书，无需重复预约");
        }
        if (book.getAvailableCount() != null && book.getAvailableCount() > 0) {
            return ApiResponse.error("图书还有库存，可直接借阅，无需预约");
        }
        BookReservation reservation = BookReservation.builder()
                .userId(userId)
                .bookId(bookId)
                .reserveTime(LocalDateTime.now())
                .status(0)
                .build();
        try {
            int inserted = bookReservationMapper.insertIfNoActiveReservation(reservation);
            if (inserted == 0) {
                return ApiResponse.error("您已预约过该图书，请勿重复预约");
            }
        } catch (DuplicateKeyException e) {
            return ApiResponse.error("您已预约过该图书，请勿重复预约");
        }
        return ApiResponse.success("预约成功，图书到货后会通知您");
    }

    @Override
    @Transactional
    public ApiResponse<Void> cancel(Integer reservationId) {
        Integer userId = CurrentUserContext.userId();
        BookReservation reservation = bookReservationMapper.getById(reservationId);
        if (reservation == null) {
            return ApiResponse.error("预约记录不存在");
        }
        if (!reservation.getUserId().equals(userId)) {
            return ApiResponse.error("该预约记录不属于当前用户");
        }
        if (reservation.getStatus() != 0) {
            return ApiResponse.error("该预约已处理，无法取消");
        }
        if (bookReservationMapper.cancelWaiting(reservationId, userId) == 0) {
            return ApiResponse.error("该预约状态已变化，请刷新后重试");
        }
        return ApiResponse.success("取消预约成功");
    }

    @Override
    public ApiResponse<List<BookReservationView>> query(BookReservationPageQuery dto) {
        if (dto == null) {
            dto = new BookReservationPageQuery();
        }
        if (!CurrentUserContext.isAdministrator()) {
            dto.setUserId(CurrentUserContext.userId());
        }
        List<BookReservationView> list = bookReservationMapper.query(dto);
        Integer total = bookReservationMapper.queryCount(dto);
        return PageResponse.success(list, total);
    }

}
