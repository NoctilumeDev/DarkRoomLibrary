package org.darkroomlibrary.service;

import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.view.RecommendationFeedView;
import org.darkroomlibrary.web.view.RecommendationSettingView;

public interface RecommendationService {
    ApiResponse<RecommendationFeedView> feed(Integer size);

    ApiResponse<RecommendationSettingView> setting();

    ApiResponse<RecommendationSettingView> updateSetting(Boolean enabled);

    ApiResponse<Void> clearHistory();

    ApiResponse<Void> recordEvent(Long itemId, String eventType);

    void attributeFavorite(Integer userId, Integer bookId);
}
