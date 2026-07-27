package org.darkroomlibrary.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.darkroomlibrary.domain.model.RecommendationBatch;
import org.darkroomlibrary.domain.model.RecommendationEvent;
import org.darkroomlibrary.domain.model.RecommendationItem;
import org.darkroomlibrary.domain.model.RecommendationSetting;
import org.darkroomlibrary.domain.recommendation.RecommendationBookProfile;
import org.darkroomlibrary.domain.recommendation.RecommendationFavoriteLink;
import org.darkroomlibrary.domain.recommendation.RecommendationUserSignal;
import org.darkroomlibrary.web.view.RecommendationItemView;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RecommendationMapper {
    RecommendationSetting findSetting(@Param("userId") Integer userId);

    int insertSetting(RecommendationSetting setting);

    int updateSetting(RecommendationSetting setting);

    RecommendationBatch findReusableBatch(@Param("userId") Integer userId,
                                           @Param("sourceFingerprint") String sourceFingerprint,
                                           @Param("now") LocalDateTime now);

    int insertBatch(RecommendationBatch batch);

    int insertItem(RecommendationItem item);

    List<RecommendationItemView> findItems(@Param("batchId") Long batchId);

    RecommendationItem findOwnedItem(@Param("userId") Integer userId,
                                     @Param("itemId") Long itemId);

    RecommendationItem findLatestExposedItem(@Param("userId") Integer userId,
                                             @Param("bookId") Integer bookId,
                                             @Param("since") LocalDateTime since);

    int insertEvent(RecommendationEvent event);

    List<Integer> findDismissedBookIds(@Param("userId") Integer userId,
                                       @Param("since") LocalDateTime since);

    int deleteBatchesByUser(@Param("userId") Integer userId);

    int deleteItemsByUser(@Param("userId") Integer userId);

    int deleteEventsByUser(@Param("userId") Integer userId);

    int pruneExpiredBatches(@Param("userId") Integer userId,
                            @Param("cutoff") LocalDateTime cutoff);

    List<RecommendationBookProfile> findActiveBookProfiles();

    List<RecommendationUserSignal> findUserSignals(@Param("userId") Integer userId);

    List<RecommendationFavoriteLink> findFavoriteLinks(@Param("userId") Integer userId);
}
