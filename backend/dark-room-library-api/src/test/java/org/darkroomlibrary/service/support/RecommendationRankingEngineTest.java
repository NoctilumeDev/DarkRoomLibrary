package org.darkroomlibrary.service.support;

import org.darkroomlibrary.domain.recommendation.RecommendationBookProfile;
import org.darkroomlibrary.domain.recommendation.RecommendationFavoriteLink;
import org.darkroomlibrary.domain.recommendation.RecommendationUserSignal;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationRankingEngineTest {

    private final RecommendationRankingEngine engine = new RecommendationRankingEngine();
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 1, 12, 0);

    @Test
    void fewerThanThreeFavoritesUsesPublicFallback() {
        List<RecommendationBookProfile> books = books();
        List<RecommendationUserSignal> signals = List.of(favorite(1), favorite(2));

        var plan = engine.rank(10, books, signals, List.of(), true, 6, now);

        assertEquals("PUBLIC", plan.mode());
        assertFalse(plan.personalized());
        assertEquals(2, plan.signalCount());
        assertTrue(plan.items().stream().noneMatch(item -> List.of(1, 2).contains(item.book().getId())));
    }

    @Test
    void contentModeFiltersOwnedAndNegativeBooksAndDiversifiesResults() {
        List<RecommendationBookProfile> books = books();
        List<RecommendationUserSignal> signals = new ArrayList<>(List.of(
                favorite(1), favorite(2), favorite(3), negativeReview(4), activeBorrow(5)
        ));

        var plan = engine.rank(10, books, signals, List.of(), true, 6, now);

        assertEquals("CONTENT", plan.mode());
        assertTrue(plan.personalized());
        assertTrue(plan.items().stream().noneMatch(item -> item.book().getId() <= 5));
        assertTrue(plan.items().stream().allMatch(item -> !item.reason().isBlank()));
        long literatureCount = plan.items().stream()
                .filter(item -> "文学".equals(item.book().getCategory())).count();
        assertTrue(literatureCount <= 2);
    }

    @Test
    void collaborativeWeightOnlyActivatesAfterTwoIndependentCoFavorites() {
        List<RecommendationBookProfile> books = books();
        List<RecommendationUserSignal> signals = List.of(favorite(1), favorite(2), favorite(3));
        List<RecommendationFavoriteLink> links = List.of(
                link(10, 1), link(10, 2), link(10, 3),
                link(21, 1), link(21, 8),
                link(22, 1), link(22, 8)
        );

        var plan = engine.rank(10, books, signals, links, true, 6, now);

        assertEquals("HYBRID", plan.mode());
        var collaborative = plan.items().stream()
                .filter(item -> item.book().getId() == 8)
                .findFirst().orElseThrow();
        assertTrue(collaborative.collaborativeScore() > 0d);
        assertTrue(collaborative.reason().contains("收藏过"));
    }

    @Test
    void rankingIsDeterministicForSameDayAndInputs() {
        List<RecommendationBookProfile> books = books();
        List<RecommendationUserSignal> signals = List.of(favorite(1), favorite(2), favorite(3));

        var first = engine.rank(10, books, signals, List.of(), true, 6, now);
        var second = engine.rank(10, books, signals, List.of(), true, 6, now);

        assertEquals(
                first.items().stream().map(item -> item.book().getId()).toList(),
                second.items().stream().map(item -> item.book().getId()).toList()
        );
    }

    private List<RecommendationBookProfile> books() {
        return List.of(
                book(1, "山水初卷", "甲", "文学", "山水 夜色 行旅"),
                book(2, "山居次卷", "乙", "文学", "山水 清寂 归隐"),
                book(3, "自由札记", "丙", "哲学", "自由 生命 思想"),
                book(4, "负向书评", "丁", "哲学", "自由 思想"),
                book(5, "案头借阅", "戊", "历史", "旧城 档案"),
                book(6, "林下微光", "己", "文学", "山水 清寂 林间"),
                book(7, "魏晋风骨", "庚", "历史", "归隐 山水 魏晋"),
                book(8, "远灯偶遇", "辛", "科学", "宇宙 星光"),
                book(9, "生命之问", "壬", "哲学", "自由 生命"),
                book(10, "纸上旧城", "癸", "历史", "旧城 阅读 档案")
        );
    }

    private RecommendationBookProfile book(int id, String name, String author,
                                           String category, String description) {
        RecommendationBookProfile book = new RecommendationBookProfile();
        book.setId(id);
        book.setVersion(0);
        book.setName(name);
        book.setAuthor(author);
        book.setPublisher("测试书局");
        book.setCategory(category);
        book.setDescription(description);
        book.setTotalCount(4);
        book.setAvailableCount(3);
        book.setFavoriteCount(id == 8 ? 2 : 1);
        book.setBorrowCount(id % 3);
        book.setReviewCount(1);
        book.setAverageRating(4d);
        book.setCreateTime(now.minusDays(id * 4L));
        return book;
    }

    private RecommendationUserSignal favorite(int bookId) {
        RecommendationUserSignal signal = new RecommendationUserSignal();
        signal.setBookId(bookId);
        signal.setFavoriteCount(1);
        signal.setBorrowCount(0);
        signal.setActiveBorrowCount(0);
        signal.setReviewCount(0);
        signal.setAverageRating(0d);
        signal.setLatestInteractionTime(now.minusDays(bookId));
        return signal;
    }

    private RecommendationUserSignal negativeReview(int bookId) {
        RecommendationUserSignal signal = favorite(bookId);
        signal.setFavoriteCount(0);
        signal.setReviewCount(1);
        signal.setAverageRating(2d);
        return signal;
    }

    private RecommendationUserSignal activeBorrow(int bookId) {
        RecommendationUserSignal signal = favorite(bookId);
        signal.setFavoriteCount(0);
        signal.setBorrowCount(1);
        signal.setActiveBorrowCount(1);
        return signal;
    }

    private RecommendationFavoriteLink link(int userId, int bookId) {
        RecommendationFavoriteLink link = new RecommendationFavoriteLink();
        link.setUserId(userId);
        link.setBookId(bookId);
        return link;
    }
}
