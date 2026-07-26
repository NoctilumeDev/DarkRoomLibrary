package org.darkroomlibrary.controller;

import org.darkroomlibrary.aop.NormalizePageQuery;
import org.darkroomlibrary.aop.RequireRole;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.BookPageQuery;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.web.view.MetricPoint;
import org.darkroomlibrary.service.BookService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;

/**
 * 图书控制器
 */
@RestController
@RequestMapping("/book")
public class BookController {

    @Resource
    private BookService bookService;

    /**
     * 新增图书
     *
     * @param book 图书信息
     * @return ApiResponse<Void>
     */
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/save")
    @ResponseBody
    public ApiResponse<Void> save(@Valid @RequestBody Book book) {
        return bookService.save(book);
    }

    /**
     * 修改图书
     *
     * @param book 图书信息
     * @return ApiResponse<Void>
     */
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PutMapping("/update")
    @ResponseBody
    public ApiResponse<Void> update(@Valid @RequestBody Book book) {
        return bookService.update(book);
    }

    /**
     * 批量删除图书（软删除）
     *
     * @param ids 图书ID集合
     * @return ApiResponse<Void>
     */
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/batchDelete")
    @ResponseBody
    public ApiResponse<Void> batchDelete(@RequestBody List<Integer> ids) {
        return bookService.batchDelete(ids);
    }

    /**
     * 恢复已删除的图书
     *
     * @param ids 图书ID集合
     * @return ApiResponse<Void>
     */
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/restore")
    @ResponseBody
    public ApiResponse<Void> restore(@RequestBody List<Integer> ids) {
        return bookService.restore(ids);
    }

    /**
     * 分页查询图书
     *
     * @param dto 查询参数
     * @return ApiResponse<List<Book>>
     */
    @NormalizePageQuery
    @PostMapping("/query")
    @ResponseBody
    public ApiResponse<List<Book>> query(@RequestBody BookPageQuery dto) {
        return bookService.query(dto);
    }

    /**
     * 统计近N天新增图书
     */
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/queryByDays/{day}")
    @ResponseBody
    public ApiResponse<List<MetricPoint>> queryByDays(@PathVariable Integer day) {
        return bookService.queryByDays(day);
    }
}
