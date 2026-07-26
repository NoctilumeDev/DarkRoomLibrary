package org.darkroomlibrary.controller;

import org.darkroomlibrary.aop.NormalizePageQuery;
import org.darkroomlibrary.aop.RequireRole;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.BookFavoritePageQuery;
import org.darkroomlibrary.web.view.BookFavoriteView;
import org.darkroomlibrary.service.BookFavoriteService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 图书收藏控制器
 */
@RestController
@RequestMapping("/bookFavorite")
public class BookFavoriteController {

    @Resource
    private BookFavoriteService bookFavoriteService;

    /**
     * 添加收藏
     */
    @RequireRole(UserRole.READER)
    @PostMapping("/add/{bookId}")
    public ApiResponse<Void> addFavorite(@PathVariable Integer bookId) {
        return bookFavoriteService.addFavorite(bookId);
    }

    @RequireRole(UserRole.READER)
    @PostMapping("/remove/{bookId}")
    public ApiResponse<Void> removeFavorite(@PathVariable Integer bookId) {
        return bookFavoriteService.removeFavorite(bookId);
    }

    @RequireRole(UserRole.READER)
    @GetMapping("/isFavorited/{bookId}")
    public ApiResponse<Boolean> isFavorited(@PathVariable Integer bookId) {
        return bookFavoriteService.isFavorited(bookId);
    }

    @NormalizePageQuery
    @RequireRole(UserRole.READER)
    @PostMapping("/query")
    public ApiResponse<List<BookFavoriteView>> query(@RequestBody BookFavoritePageQuery dto) {
        return bookFavoriteService.query(dto);
    }
}
