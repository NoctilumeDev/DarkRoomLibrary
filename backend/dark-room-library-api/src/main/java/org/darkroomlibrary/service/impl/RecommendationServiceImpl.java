package org.darkroomlibrary.service.impl;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.domain.model.RecommendationBatch;
import org.darkroomlibrary.domain.model.RecommendationEvent;
import org.darkroomlibrary.domain.model.RecommendationItem;
import org.darkroomlibrary.domain.model.RecommendationSetting;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.domain.recommendation.RecommendationBookProfile;
import org.darkroomlibrary.domain.recommendation.RecommendationFavoriteLink;
import org.darkroomlibrary.domain.recommendation.RecommendationUserSignal;
import org.darkroomlibrary.domain.type.AccountStatus;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.mapper.RecommendationMapper;
import org.darkroomlibrary.mapper.UserMapper;
import org.darkroomlibrary.service.RecommendationService;
import org.darkroomlibrary.service.support.RecommendationRankingEngine;
import org.darkroomlibrary.service.support.RecommendationRankingEngine.RankedRecommendation;
import org.darkroomlibrary.service.support.RecommendationRankingEngine.RecommendationPlan;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.view.RecommendationFeedView;
import org.darkroomlibrary.web.view.RecommendationItemView;
import org.darkroomlibrary.web.view.RecommendationSettingView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Service
public class RecommendationServiceImpl implements RecommendationService {

    private static final int DEFAULT_FEED_SIZE = 6;
    private static final int MAX_GENERATED_ITEMS = 8;
    private static final int PERSONALIZATION_THRESHOLD = 3;
    private static final String DATA_SCOPE = "只使用你主动留下的收藏、借阅与评分，不记录无关浏览行为。";
    private static final String CLEAR_EFFECT = "清除曝光、点击与计算结果，不删除收藏、借阅或书评。";

    @Resource
    private RecommendationMapper recommendationMapper;

    @Resource
    private RecommendationRankingEngine rankingEngine;

    @Resource
    private UserMapper userMapper;

    @Override
    @Transactional
    public ApiResponse<RecommendationFeedView> feed(Integer requestedSize) {
        Integer userId = CurrentUserContext.userId();
        if (!isReader(userId)) return ApiResponse.error("当前身份不能读取读者推荐");
        int size = requestedSize == null
                ? DEFAULT_FEED_SIZE : Math.max(1, Math.min(requestedSize, MAX_GENERATED_ITEMS));
        RecommendationSetting setting = recommendationMapper.findSetting(userId);
        boolean enabled = setting == null || Boolean.TRUE.equals(setting.getEnabled());
        List<RecommendationBookProfile> books = recommendationMapper.findActiveBookProfiles();
        List<RecommendationUserSignal> signals = enabled
                ? recommendationMapper.findUserSignals(userId) : List.of();
        long favoriteCount = signals.stream()
                .filter(signal -> signal.getFavoriteCount() != null && signal.getFavoriteCount() > 0)
                .count();
        List<RecommendationFavoriteLink> links = favoriteCount >= PERSONALIZATION_THRESHOLD
                ? recommendationMapper.findFavoriteLinks(userId) : List.of();
        LocalDateTime now = now();
        String fingerprint = fingerprint(enabled, books, signals, links);
        RecommendationBatch batch = recommendationMapper.findReusableBatch(userId, fingerprint, now);
        if (batch == null) {
            RecommendationPlan plan = rankingEngine.rank(userId, books, signals, links,
                    enabled, MAX_GENERATED_ITEMS, now);
            batch = persistPlan(userId, fingerprint, plan, now);
            recommendationMapper.pruneExpiredBatches(userId, now.minusDays(90));
        }
        List<RecommendationItemView> items = recommendationMapper.findItems(batch.getId())
                .stream().limit(size).toList();
        for (RecommendationItemView item : items) {
            insertUniqueEvent(userId, item.getItemId(), "EXPOSE", now);
        }
        return ApiResponse.success(RecommendationFeedView.builder()
                .mode(batch.getMode())
                .personalized(!"PUBLIC".equals(batch.getMode()))
                .enabled(enabled)
                .signalCount(batch.getSignalCount())
                .generatedAt(batch.getGeneratedAt())
                .privacyNotice(DATA_SCOPE)
                .items(items)
                .build());
    }

    @Override
    public ApiResponse<RecommendationSettingView> setting() {
        Integer userId = CurrentUserContext.userId();
        if (!isReaderContext(userId)) return ApiResponse.error("当前身份不能读取推荐设置");
        RecommendationSetting setting = recommendationMapper.findSetting(userId);
        return ApiResponse.success(settingView(setting == null || Boolean.TRUE.equals(setting.getEnabled())));
    }

    @Override
    @Transactional
    public ApiResponse<RecommendationSettingView> updateSetting(Boolean enabled) {
        Integer userId = CurrentUserContext.userId();
        if (enabled == null || !isReader(userId)) return ApiResponse.error("推荐设置无效");
        RecommendationSetting setting = RecommendationSetting.builder()
                .userId(userId)
                .enabled(enabled)
                .updatedAt(now())
                .build();
        if (recommendationMapper.updateSetting(setting) == 0) {
            try {
                recommendationMapper.insertSetting(setting);
            } catch (DuplicateKeyException ignored) {
                recommendationMapper.updateSetting(setting);
            }
        }
        return ApiResponse.success(enabled ? "个性化推荐已开启" : "已改为公共荐书", settingView(enabled));
    }

    @Override
    @Transactional
    public ApiResponse<Void> clearHistory() {
        Integer userId = CurrentUserContext.userId();
        if (!isReader(userId)) return ApiResponse.error("当前身份不能清除推荐记录");
        recommendationMapper.deleteEventsByUser(userId);
        recommendationMapper.deleteItemsByUser(userId);
        recommendationMapper.deleteBatchesByUser(userId);
        return ApiResponse.success("推荐记录已清除，收藏、借阅和书评保持不变");
    }

    @Override
    @Transactional
    public ApiResponse<Void> recordEvent(Long itemId, String eventType) {
        Integer userId = CurrentUserContext.userId();
        if (itemId == null || !List.of("CLICK", "DISMISS").contains(eventType)
                || !isReaderContext(userId)) {
            return ApiResponse.error("推荐事件无效");
        }
        RecommendationItem item = recommendationMapper.findOwnedItem(userId, itemId);
        if (item == null) return ApiResponse.error("推荐条目不存在或不属于当前读者");
        insertUniqueEvent(userId, itemId, eventType, now());
        return ApiResponse.success();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attributeFavorite(Integer userId, Integer bookId) {
        if (userId == null || bookId == null) return;
        RecommendationItem item = recommendationMapper.findLatestExposedItem(
                userId, bookId, now().minusDays(7));
        if (item != null) {
            insertUniqueEvent(userId, item.getId(), "FAVORITE", now());
        }
    }

    private RecommendationBatch persistPlan(Integer userId,
                                            String fingerprint,
                                            RecommendationPlan plan,
                                            LocalDateTime now) {
        RecommendationBatch batch = RecommendationBatch.builder()
                .userId(userId)
                .mode(plan.mode())
                .algorithmVersion(RecommendationRankingEngine.ALGORITHM_VERSION)
                .signalCount(plan.signalCount())
                .sourceFingerprint(fingerprint)
                .generatedAt(now)
                .expiresAt(now.plusHours(6))
                .build();
        recommendationMapper.insertBatch(batch);
        for (RankedRecommendation ranked : plan.items()) {
            RecommendationItem item = RecommendationItem.builder()
                    .batchId(batch.getId())
                    .userId(userId)
                    .bookId(ranked.book().getId())
                    .rankNo(ranked.rank())
                    .totalScore(score(ranked.totalScore()))
                    .contentScore(score(ranked.contentScore()))
                    .collaborativeScore(score(ranked.collaborativeScore()))
                    .qualityScore(score(ranked.qualityScore()))
                    .explorationScore(score(ranked.explorationScore()))
                    .sourceType(ranked.sourceType())
                    .reason(ranked.reason())
                    .build();
            recommendationMapper.insertItem(item);
        }
        return batch;
    }

    private RecommendationSettingView settingView(boolean enabled) {
        return RecommendationSettingView.builder()
                .enabled(enabled)
                .dataScope(DATA_SCOPE)
                .clearEffect(CLEAR_EFFECT)
                .build();
    }

    private void insertUniqueEvent(Integer userId, Long itemId, String eventType, LocalDateTime time) {
        try {
            recommendationMapper.insertEvent(RecommendationEvent.builder()
                    .userId(userId)
                    .itemId(itemId)
                    .eventType(eventType)
                    .createdAt(time)
                    .build());
        } catch (DuplicateKeyException ignored) {
            // One immutable event of each type per recommendation item is sufficient for attribution.
        }
    }

    private boolean isReader(Integer userId) {
        if (!isReaderContext(userId)) return false;
        User user = userMapper.findByIdForUpdate(userId);
        return user != null
                && Objects.equals(user.getUserRole(), UserRole.READER.code())
                && Objects.equals(user.getAccountStatus(), AccountStatus.NORMAL.code())
                && !Boolean.TRUE.equals(user.getIsLogin());
    }

    private boolean isReaderContext(Integer userId) {
        return userId != null && Objects.equals(CurrentUserContext.roleCode(), UserRole.READER.code());
    }

    private BigDecimal score(double value) {
        double safe = Double.isFinite(value) ? Math.max(0d, value) : 0d;
        return BigDecimal.valueOf(safe).setScale(6, RoundingMode.HALF_UP);
    }

    private LocalDateTime now() {
        return LocalDateTime.now().withNano(0);
    }

    private String fingerprint(boolean enabled,
                               List<RecommendationBookProfile> books,
                               List<RecommendationUserSignal> signals,
                               List<RecommendationFavoriteLink> links) {
        StringBuilder source = new StringBuilder(enabled ? "1|" : "0|");
        books.stream().sorted(Comparator.comparing(RecommendationBookProfile::getId)).forEach(book ->
                source.append('b').append(book.getId()).append(':').append(book.getVersion())
                        .append(':').append(book.getFavoriteCount()).append(':').append(book.getBorrowCount())
                        .append(':').append(book.getReviewCount()).append(':').append(book.getAverageRating()).append('|'));
        signals.stream().sorted(Comparator.comparing(RecommendationUserSignal::getBookId)).forEach(signal ->
                source.append('s').append(signal.getBookId()).append(':').append(signal.getFavoriteCount())
                        .append(':').append(signal.getBorrowCount()).append(':').append(signal.getActiveBorrowCount())
                        .append(':').append(signal.getReviewCount()).append(':').append(signal.getAverageRating())
                        .append(':').append(signal.getLatestInteractionTime()).append('|'));
        links.stream().sorted(Comparator.comparing(RecommendationFavoriteLink::getUserId)
                        .thenComparing(RecommendationFavoriteLink::getBookId))
                .forEach(link -> source.append('f').append(link.getUserId()).append(':')
                        .append(link.getBookId()).append('|'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
