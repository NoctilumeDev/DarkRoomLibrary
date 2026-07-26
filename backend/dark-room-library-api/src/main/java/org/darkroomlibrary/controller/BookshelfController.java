package org.darkroomlibrary.controller;

import org.darkroomlibrary.aop.NormalizePageQuery;
import org.darkroomlibrary.aop.RequireRole;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.BookshelfPageQuery;
import org.darkroomlibrary.domain.model.Bookshelf;
import org.darkroomlibrary.service.BookshelfService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;

/**
 * 书架控制器
 */
@RestController
@RequestMapping("/bookshelf")
public class BookshelfController {

    @Resource
    private BookshelfService bookshelfService;

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/save")
    public ApiResponse<Void> save(@Valid @RequestBody Bookshelf bookshelf) {
        return bookshelfService.save(bookshelf);
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PutMapping("/update")
    public ApiResponse<Void> update(@Valid @RequestBody Bookshelf bookshelf) {
        return bookshelfService.update(bookshelf);
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/batchDelete")
    public ApiResponse<Void> batchDelete(@RequestBody List<Integer> ids) {
        return bookshelfService.batchDelete(ids);
    }

    @NormalizePageQuery
    @PostMapping("/query")
    public ApiResponse<List<Bookshelf>> query(@RequestBody BookshelfPageQuery dto) {
        return bookshelfService.query(dto);
    }

    @GetMapping("/queryAll")
    public ApiResponse<List<Bookshelf>> queryAll() {
        return bookshelfService.queryAll();
    }
}
