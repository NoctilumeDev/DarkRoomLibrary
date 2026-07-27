package org.darkroomlibrary.service.impl;

import jakarta.annotation.Resource;
import org.darkroomlibrary.BaseTest;
import org.darkroomlibrary.domain.model.Book;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.service.BookFavoriteService;
import org.darkroomlibrary.service.RecommendationService;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.view.RecommendationFeedView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.List;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecommendationServiceImplTest extends BaseTest {

    @Resource
    private RecommendationService recommendationService;

    @Resource
    private BookFavoriteService bookFavoriteService;

    @Resource
    private JdbcTemplate jdbcTemplate;

    private User reader;
    private List<Book> books;

    @BeforeEach
    void setUp() {
        clearContext();
        String suffix = String.valueOf(System.nanoTime());
        reader = createTestUser("recommend_" + suffix, "荐书读者" + suffix,
                "recommend_" + suffix + "@example.test");
        books = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            Book book = createTestBook("荐书样本-" + suffix + "-" + index,
                    "作者-" + index, 3);
            book.setCategory(index < 5 ? "文学" : index < 7 ? "哲学" : "历史");
            book.setDescription(index < 5 ? "山水 夜色 阅读 归隐" : "自由 生命 思想 档案");
            bookMapper.updateById(book);
            books.add(book);
        }
        setCurrentUser(reader.getId(), reader.getUserRole());
    }

    @Test
    void feedMovesFromPublicToContentAndReusesBatch() {
        ApiResponse<RecommendationFeedView> publicFeed = recommendationService.feed(6);
        assertEquals(200, publicFeed.getCode());
        assertEquals("PUBLIC", publicFeed.getData().getMode());
        assertFalse(publicFeed.getData().getPersonalized());

        for (int index = 0; index < 3; index++) {
            assertEquals(200, bookFavoriteService.addFavorite(books.get(index).getId()).getCode());
        }
        ApiResponse<RecommendationFeedView> personalized = recommendationService.feed(6);
        ApiResponse<RecommendationFeedView> reused = recommendationService.feed(6);

        assertEquals("CONTENT", personalized.getData().getMode());
        assertTrue(personalized.getData().getPersonalized());
        assertEquals(3, personalized.getData().getSignalCount());
        assertFalse(personalized.getData().getItems().isEmpty());
        assertTrue(Duration.between(personalized.getData().getGeneratedAt(),
                reused.getData().getGeneratedAt()).abs().toMillis() <= 1);
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recommendation_batch WHERE user_id = ?",
                Integer.class, reader.getId()));
        Integer duplicateExposureCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recommendation_event WHERE user_id = ? AND event_type = 'EXPOSE'",
                Integer.class, reader.getId());
        assertEquals(publicFeed.getData().getItems().size()
                + personalized.getData().getItems().size(), duplicateExposureCount);
    }

    @Test
    void settingsAreTransparentAndDisablingFallsBackToPublic() {
        assertTrue(recommendationService.setting().getData().getEnabled());
        assertTrue(recommendationService.setting().getData().getDataScope().contains("收藏"));
        for (int index = 0; index < 3; index++) {
            bookFavoriteService.addFavorite(books.get(index).getId());
        }

        var updated = recommendationService.updateSetting(false);
        var feed = recommendationService.feed(6);

        assertEquals(200, updated.getCode());
        assertFalse(updated.getData().getEnabled());
        assertEquals("PUBLIC", feed.getData().getMode());
        assertEquals(0, feed.getData().getSignalCount());
    }

    @Test
    void clickAndFavoriteAttributionAreIdempotentAndHistoryCanBeCleared() {
        for (int index = 0; index < 3; index++) {
            bookFavoriteService.addFavorite(books.get(index).getId());
        }
        RecommendationFeedView feed = recommendationService.feed(6).getData();
        var item = feed.getItems().get(0);

        assertEquals(200, recommendationService.recordEvent(item.getItemId(), "CLICK").getCode());
        assertEquals(200, recommendationService.recordEvent(item.getItemId(), "CLICK").getCode());
        assertEquals(200, bookFavoriteService.addFavorite(item.getBookId()).getCode());

        Integer clickCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recommendation_event WHERE item_id = ? AND event_type = 'CLICK'",
                Integer.class, item.getItemId());
        Integer favoriteCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recommendation_event WHERE item_id = ? AND event_type = 'FAVORITE'",
                Integer.class, item.getItemId());
        assertEquals(1, clickCount);
        assertEquals(1, favoriteCount);

        assertEquals(200, recommendationService.clearHistory().getCode());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recommendation_batch WHERE user_id = ?", Integer.class, reader.getId()));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recommendation_event WHERE user_id = ?", Integer.class, reader.getId()));
    }

    @Test
    void recommendationItemCannotBeClaimedByAnotherReader() {
        RecommendationFeedView feed = recommendationService.feed(3).getData();
        assertNotNull(feed);
        User other = createTestUser("recommend_other_" + System.nanoTime(), "另一读者",
                "recommend_other_" + System.nanoTime() + "@example.test");
        setCurrentUser(other.getId(), other.getUserRole());

        assertEquals(400, recommendationService.recordEvent(
                feed.getItems().get(0).getItemId(), "CLICK").getCode());
    }
}
