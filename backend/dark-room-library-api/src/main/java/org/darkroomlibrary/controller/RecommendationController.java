package org.darkroomlibrary.controller;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.darkroomlibrary.aop.RequireRole;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.service.RecommendationService;
import org.darkroomlibrary.web.dto.command.RecommendationEventDto;
import org.darkroomlibrary.web.dto.command.RecommendationSettingUpdateDto;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.view.RecommendationFeedView;
import org.darkroomlibrary.web.view.RecommendationSettingView;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recommendation")
public class RecommendationController {

    @Resource
    private RecommendationService recommendationService;

    @RequireRole(UserRole.READER)
    @GetMapping("/feed")
    public ApiResponse<RecommendationFeedView> feed(@RequestParam(required = false) Integer size) {
        return recommendationService.feed(size);
    }

    @RequireRole(UserRole.READER)
    @GetMapping("/setting")
    public ApiResponse<RecommendationSettingView> setting() {
        return recommendationService.setting();
    }

    @RequireRole(UserRole.READER)
    @PutMapping("/setting")
    public ApiResponse<RecommendationSettingView> updateSetting(
            @Valid @RequestBody RecommendationSettingUpdateDto dto) {
        return recommendationService.updateSetting(dto.getEnabled());
    }

    @RequireRole(UserRole.READER)
    @DeleteMapping("/history")
    public ApiResponse<Void> clearHistory() {
        return recommendationService.clearHistory();
    }

    @RequireRole(UserRole.READER)
    @PostMapping("/items/{itemId}/events")
    public ApiResponse<Void> recordEvent(@PathVariable Long itemId,
                                         @Valid @RequestBody RecommendationEventDto dto) {
        return recommendationService.recordEvent(itemId, dto.getEventType());
    }
}
