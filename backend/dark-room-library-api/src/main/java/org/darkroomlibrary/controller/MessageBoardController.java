package org.darkroomlibrary.controller;

import org.darkroomlibrary.aop.NormalizePageQuery;
import org.darkroomlibrary.aop.RequireRole;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.query.MessageBoardPageQuery;
import org.darkroomlibrary.web.dto.command.MessageReplyDto;
import org.darkroomlibrary.domain.model.MessageBoard;
import org.darkroomlibrary.web.view.MessageBoardView;
import org.darkroomlibrary.service.MessageBoardService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import jakarta.validation.Valid;

/**
 * 留言板控制器
 */
@RestController
@RequestMapping("/messageBoard")
public class MessageBoardController {

    @Resource
    private MessageBoardService messageBoardService;

    /**
     * 新增留言
     */
    @RequireRole(UserRole.READER)
    @PostMapping("/save")
    public ApiResponse<Void> save(@RequestBody MessageBoard messageBoard) {
        return messageBoardService.save(messageBoard);
    }

    @RequireRole({UserRole.READER, UserRole.ADMIN})
    @PostMapping("/batchDelete")
    public ApiResponse<Void> batchDelete(@RequestBody List<Integer> ids) {
        return messageBoardService.batchDelete(ids);
    }

    @NormalizePageQuery
    @RequireRole({UserRole.READER, UserRole.ADMIN})
    @PostMapping("/query")
    public ApiResponse<List<MessageBoardView>> query(@RequestBody MessageBoardPageQuery dto) {
        return messageBoardService.query(dto);
    }

    @RequireRole(UserRole.ADMIN)
    @PutMapping("/reply")
    public ApiResponse<Void> reply(@Valid @RequestBody MessageReplyDto dto) {
        return messageBoardService.reply(dto.getId(), dto.getReply());
    }
}
