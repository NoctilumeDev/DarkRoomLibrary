package org.darkroomlibrary.service;

import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.BookReservationPageQuery;
import org.darkroomlibrary.web.view.BookReservationView;

import java.util.List;

/**
 * 图书预约服务接口
 */
public interface BookReservationService {

    ApiResponse<Void> reserve(Integer bookId);

    ApiResponse<Void> cancel(Integer reservationId);

    ApiResponse<List<BookReservationView>> query(BookReservationPageQuery dto);
}