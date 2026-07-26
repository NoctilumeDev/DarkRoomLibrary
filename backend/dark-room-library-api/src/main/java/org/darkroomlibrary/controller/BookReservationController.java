package org.darkroomlibrary.controller;

import org.darkroomlibrary.aop.NormalizePageQuery;
import org.darkroomlibrary.aop.RequireRole;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.BookReservationPageQuery;
import org.darkroomlibrary.web.view.BookReservationView;
import org.darkroomlibrary.service.BookReservationService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 图书预约控制器
 */
@RestController
@RequestMapping("/bookReservation")
public class BookReservationController {

    @Resource
    private BookReservationService bookReservationService;

    /**
     * 预约图书
     */
    @RequireRole(UserRole.READER)
    @PostMapping("/reserve/{bookId}")
    public ApiResponse<Void> reserve(@PathVariable Integer bookId) {
        return bookReservationService.reserve(bookId);
    }

    @RequireRole(UserRole.READER)
    @PostMapping("/cancel/{reservationId}")
    public ApiResponse<Void> cancel(@PathVariable Integer reservationId) {
        return bookReservationService.cancel(reservationId);
    }

    @NormalizePageQuery
    @RequireRole({UserRole.READER, UserRole.ADMIN})
    @PostMapping("/query")
    public ApiResponse<List<BookReservationView>> query(@RequestBody BookReservationPageQuery dto) {
        return bookReservationService.query(dto);
    }
}
