package org.darkroomlibrary.service.support;

import org.darkroomlibrary.domain.recommendation.RecommendationBookProfile;
import org.darkroomlibrary.domain.recommendation.RecommendationFavoriteLink;
import org.darkroomlibrary.domain.recommendation.RecommendationUserSignal;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RecommendationRankingEngine {

    public static final String ALGORITHM_VERSION = "bookmark-hybrid-v1";
    private static final int PERSONALIZATION_THRESHOLD = 3;
    private static final double EPSILON = 0.000001d;

    public RecommendationPlan rank(Integer userId,
                                   List<RecommendationBookProfile> bookProfiles,
                                   List<RecommendationUserSignal> userSignals,
                                   List<RecommendationFavoriteLink> favoriteLinks,
                                   boolean enabled,
                                   int limit,
                                   LocalDateTime now) {
        List<RecommendationBookProfile> books = bookProfiles == null ? List.of() : bookProfiles;
        List<RecommendationUserSignal> signals = userSignals == null ? List.of() : userSignals;
        List<RecommendationFavoriteLink> links = favoriteLinks == null ? List.of() : favoriteLinks;
        Map<Integer, RecommendationBookProfile> profilesById = books.stream()
                .collect(Collectors.toMap(RecommendationBookProfile::getId, book -> book));

        Set<Integer> favoriteBookIds = signals.stream()
                .filter(signal -> value(signal.getFavoriteCount()) > 0)
                .map(RecommendationUserSignal::getBookId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Integer> excludedBookIds = signals.stream()
                .filter(signal -> value(signal.getFavoriteCount()) > 0
                        || value(signal.getActiveBorrowCount()) > 0
                        || (value(signal.getReviewCount()) > 0 && decimal(signal.getAverageRating()) <= 2d))
                .map(RecommendationUserSignal::getBookId)
                .collect(Collectors.toSet());

        boolean personalized = enabled && favoriteBookIds.size() >= PERSONALIZATION_THRESHOLD;
        Map<String, Double> userVector = personalized
                ? buildUserVector(signals, profilesById, now)
                : Map.of();
        CollaborativeScores collaborative = personalized
                ? collaborativeScores(favoriteBookIds, links, profilesById.keySet())
                : CollaborativeScores.empty();

        List<RecommendationBookProfile> candidates = books.stream()
                .filter(book -> !excludedBookIds.contains(book.getId()))
                .toList();
        Map<Integer, Double> qualityScores = qualityScores(candidates, now);
        Map<Integer, RecommendationUserSignal> signalsByBook = signals.stream()
                .collect(Collectors.toMap(RecommendationUserSignal::getBookId, signal -> signal));

        boolean hasCollaborativeData = collaborative.scores().values().stream()
                .anyMatch(score -> score > EPSILON);
        String mode = !personalized ? "PUBLIC" : hasCollaborativeData ? "HYBRID" : "CONTENT";
        List<ScoredCandidate> scored = new ArrayList<>();
        for (RecommendationBookProfile candidate : candidates) {
            double content = personalized
                    ? cosine(userVector, featureVector(candidate))
                    : 0d;
            double collaborativeScore = collaborative.scores().getOrDefault(candidate.getId(), 0d);
            double quality = qualityScores.getOrDefault(candidate.getId(), 0d);
            double exploration = explorationScore(userId, candidate.getId(), now.toLocalDate());
            double total;
            if (!personalized) {
                total = quality * 0.75d + exploration * 0.25d;
            } else if (hasCollaborativeData) {
                total = content * 0.55d + collaborativeScore * 0.25d
                        + quality * 0.15d + exploration * 0.05d;
            } else {
                total = content * 0.70d + quality * 0.20d + exploration * 0.10d;
            }
            RecommendationUserSignal previous = signalsByBook.get(candidate.getId());
            if (previous != null && (value(previous.getBorrowCount()) > 0
                    || value(previous.getReviewCount()) > 0)) {
                total *= 0.68d;
            }
            String sourceType = sourceType(personalized, hasCollaborativeData, content,
                    collaborativeScore, candidate, now);
            String reason = reason(sourceType, candidate, favoriteBookIds, profilesById,
                    collaborative.sourceBookByCandidate().get(candidate.getId()), quality);
            scored.add(new ScoredCandidate(candidate, total, content, collaborativeScore,
                    quality, exploration, sourceType, reason));
        }
        scored.sort(Comparator.comparingDouble(ScoredCandidate::total).reversed()
                .thenComparing(candidate -> candidate.book().getId()));

        return new RecommendationPlan(mode, personalized, favoriteBookIds.size(),
                diversify(scored, Math.max(1, Math.min(limit, 12))));
    }

    private Map<String, Double> buildUserVector(List<RecommendationUserSignal> signals,
                                                Map<Integer, RecommendationBookProfile> profilesById,
                                                LocalDateTime now) {
        Map<String, Double> vector = new HashMap<>();
        for (RecommendationUserSignal signal : signals) {
            RecommendationBookProfile book = profilesById.get(signal.getBookId());
            if (book == null) continue;
            double signalWeight = value(signal.getFavoriteCount()) * 6d
                    + Math.min(3, value(signal.getBorrowCount())) * 1.5d;
            if (value(signal.getReviewCount()) > 0 && decimal(signal.getAverageRating()) >= 4d) {
                signalWeight += (decimal(signal.getAverageRating()) - 3d) * 2d;
            }
            if (signalWeight <= 0d) continue;
            long days = signal.getLatestInteractionTime() == null
                    ? 0L
                    : Math.max(0L, ChronoUnit.DAYS.between(signal.getLatestInteractionTime(), now));
            double decay = Math.max(0.25d, Math.pow(0.5d, days / 180d));
            double weightedSignal = signalWeight * decay;
            featureVector(book).forEach((feature, weight) ->
                    vector.merge(feature, weight * weightedSignal, Double::sum));
        }
        return vector;
    }

    private Map<String, Double> featureVector(RecommendationBookProfile book) {
        Map<String, Double> vector = new LinkedHashMap<>();
        addFeature(vector, "category:", book.getCategory(), 3d);
        addFeature(vector, "author:", book.getAuthor(), 2.2d);
        addFeature(vector, "publisher:", book.getPublisher(), 0.6d);
        for (String token : textTokens(safe(book.getName()) + " " + safe(book.getDescription()))) {
            vector.put("text:" + token, 0.28d);
        }
        return vector;
    }

    private void addFeature(Map<String, Double> vector, String prefix, String value, double weight) {
        String normalized = normalize(value);
        if (!normalized.isEmpty()) vector.put(prefix + normalized, weight);
    }

    private Set<String> textTokens(String text) {
        String normalized = normalize(text);
        Set<String> tokens = new LinkedHashSet<>();
        StringBuilder hanRun = new StringBuilder();
        StringBuilder latinRun = new StringBuilder();
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                flushLatin(tokens, latinRun);
                hanRun.appendCodePoint(codePoint);
                continue;
            }
            flushHan(tokens, hanRun);
            if (Character.isLetterOrDigit(codePoint)) {
                latinRun.appendCodePoint(codePoint);
            } else {
                flushLatin(tokens, latinRun);
            }
        }
        flushHan(tokens, hanRun);
        flushLatin(tokens, latinRun);
        return tokens.stream().limit(80).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void flushHan(Set<String> tokens, StringBuilder run) {
        int[] codePoints = run.toString().codePoints().toArray();
        if (codePoints.length >= 2) {
            for (int index = 0; index < codePoints.length - 1; index++) {
                tokens.add(new String(codePoints, index, 2));
            }
        }
        run.setLength(0);
    }

    private void flushLatin(Set<String> tokens, StringBuilder run) {
        if (run.length() >= 3) tokens.add(run.toString());
        run.setLength(0);
    }

    private CollaborativeScores collaborativeScores(Set<Integer> favoriteBookIds,
                                                     List<RecommendationFavoriteLink> links,
                                                     Set<Integer> activeBookIds) {
        Map<Integer, Set<Integer>> usersByBook = new HashMap<>();
        for (RecommendationFavoriteLink link : links) {
            if (activeBookIds.contains(link.getBookId())) {
                usersByBook.computeIfAbsent(link.getBookId(), ignored -> new HashSet<>())
                        .add(link.getUserId());
            }
        }
        Map<Integer, Double> raw = new HashMap<>();
        Map<Integer, Integer> sourceByCandidate = new HashMap<>();
        for (Integer candidateId : activeBookIds) {
            if (favoriteBookIds.contains(candidateId)) continue;
            Set<Integer> candidateUsers = usersByBook.getOrDefault(candidateId, Set.of());
            double best = 0d;
            Integer bestSource = null;
            for (Integer sourceId : favoriteBookIds) {
                Set<Integer> sourceUsers = usersByBook.getOrDefault(sourceId, Set.of());
                int coCount = intersectionSize(sourceUsers, candidateUsers);
                if (coCount < 2) continue;
                double cosine = coCount / Math.sqrt((double) sourceUsers.size() * candidateUsers.size());
                double shrunk = cosine * coCount / (coCount + 2d);
                if (shrunk > best) {
                    best = shrunk;
                    bestSource = sourceId;
                }
            }
            if (bestSource != null) {
                raw.put(candidateId, best);
                sourceByCandidate.put(candidateId, bestSource);
            }
        }
        double max = raw.values().stream().mapToDouble(Double::doubleValue).max().orElse(0d);
        if (max > EPSILON) raw.replaceAll((bookId, score) -> score / max);
        return new CollaborativeScores(raw, sourceByCandidate);
    }

    private int intersectionSize(Set<Integer> left, Set<Integer> right) {
        if (left.isEmpty() || right.isEmpty()) return 0;
        Set<Integer> smaller = left.size() <= right.size() ? left : right;
        Set<Integer> larger = left.size() <= right.size() ? right : left;
        return (int) smaller.stream().filter(larger::contains).count();
    }

    private Map<Integer, Double> qualityScores(List<RecommendationBookProfile> books,
                                               LocalDateTime now) {
        Map<Integer, Double> rawPopularity = new HashMap<>();
        double maxPopularity = 0d;
        for (RecommendationBookProfile book : books) {
            double popularity = Math.log1p(value(book.getFavoriteCount()) * 3d
                    + value(book.getBorrowCount()) * 2d + value(book.getReviewCount()));
            rawPopularity.put(book.getId(), popularity);
            maxPopularity = Math.max(maxPopularity, popularity);
        }
        Map<Integer, Double> quality = new HashMap<>();
        for (RecommendationBookProfile book : books) {
            double popularity = maxPopularity <= EPSILON
                    ? 0d : rawPopularity.get(book.getId()) / maxPopularity;
            double rating = value(book.getReviewCount()) == 0
                    ? 0.5d : Math.min(1d, decimal(book.getAverageRating()) / 5d);
            long ageDays = book.getCreateTime() == null
                    ? 365L : Math.max(0L, ChronoUnit.DAYS.between(book.getCreateTime(), now));
            double freshness = Math.exp(-ageDays / 365d);
            double availability = value(book.getTotalCount()) <= 0
                    ? 0d : Math.min(1d, value(book.getAvailableCount()) / (double) value(book.getTotalCount()));
            quality.put(book.getId(), popularity * 0.5d + rating * 0.2d
                    + freshness * 0.2d + availability * 0.1d);
        }
        return quality;
    }

    private double cosine(Map<String, Double> left, Map<String, Double> right) {
        if (left.isEmpty() || right.isEmpty()) return 0d;
        double dot = 0d;
        double leftNorm = 0d;
        double rightNorm = 0d;
        for (double value : left.values()) leftNorm += value * value;
        for (Map.Entry<String, Double> entry : right.entrySet()) {
            double rightValue = entry.getValue();
            rightNorm += rightValue * rightValue;
            dot += left.getOrDefault(entry.getKey(), 0d) * rightValue;
        }
        if (leftNorm <= EPSILON || rightNorm <= EPSILON) return 0d;
        return dot / Math.sqrt(leftNorm * rightNorm);
    }

    private double explorationScore(Integer userId, Integer bookId, LocalDate day) {
        long hash = 17L;
        hash = hash * 31L + (userId == null ? 0 : userId);
        hash = hash * 31L + (bookId == null ? 0 : bookId);
        hash = hash * 31L + day.toEpochDay();
        return Math.floorMod(hash, 1000L) / 999d;
    }

    private String sourceType(boolean personalized,
                              boolean hasCollaborativeData,
                              double content,
                              double collaborative,
                              RecommendationBookProfile book,
                              LocalDateTime now) {
        if (!personalized) {
            long ageDays = book.getCreateTime() == null
                    ? Long.MAX_VALUE : ChronoUnit.DAYS.between(book.getCreateTime(), now);
            return ageDays <= 45 ? "NEW" : "PUBLIC";
        }
        if (hasCollaborativeData && collaborative * 0.25d > content * 0.55d) {
            return "COLLABORATIVE";
        }
        return content > EPSILON ? "CONTENT" : "DISCOVERY";
    }

    private String reason(String sourceType,
                          RecommendationBookProfile candidate,
                          Set<Integer> favoriteBookIds,
                          Map<Integer, RecommendationBookProfile> profilesById,
                          Integer collaborativeSourceId,
                          double quality) {
        if ("COLLABORATIVE".equals(sourceType) && collaborativeSourceId != null) {
            RecommendationBookProfile source = profilesById.get(collaborativeSourceId);
            if (source != null) return "收藏过《" + source.getName() + "》的读者，也常留下这本。";
        }
        if ("CONTENT".equals(sourceType)) {
            for (Integer favoriteId : favoriteBookIds) {
                RecommendationBookProfile source = profilesById.get(favoriteId);
                if (source != null && same(source.getAuthor(), candidate.getAuthor())) {
                    return "你曾留下《" + source.getName() + "》，这本书也出自" + candidate.getAuthor() + "。";
                }
            }
            for (Integer favoriteId : favoriteBookIds) {
                RecommendationBookProfile source = profilesById.get(favoriteId);
                if (source != null && same(source.getCategory(), candidate.getCategory())) {
                    return "沿着你收藏的「" + candidate.getCategory() + "」书签，灯下又出现了它。";
                }
            }
            return "它与你留下的几本书共享一些安静的线索。";
        }
        if ("NEW".equals(sourceType)) return "新近入藏，尚有许多页没有被谈起。";
        if (quality >= 0.72d) return "最近有人借阅、收藏或谈起这本书。";
        return "它与已有书签稍远，留作一次偶然相遇。";
    }

    private List<RankedRecommendation> diversify(List<ScoredCandidate> sorted, int limit) {
        List<ScoredCandidate> selected = new ArrayList<>();
        Set<Integer> selectedIds = new HashSet<>();
        Map<String, Integer> categoryCounts = new HashMap<>();
        Map<String, Integer> authorCounts = new HashMap<>();
        for (ScoredCandidate candidate : sorted) {
            String category = normalize(candidate.book().getCategory());
            String author = normalize(candidate.book().getAuthor());
            if (categoryCounts.getOrDefault(category, 0) >= 2
                    || authorCounts.getOrDefault(author, 0) >= 1) continue;
            selected.add(candidate);
            selectedIds.add(candidate.book().getId());
            categoryCounts.merge(category, 1, Integer::sum);
            authorCounts.merge(author, 1, Integer::sum);
            if (selected.size() == limit) break;
        }
        if (selected.size() < limit) {
            for (ScoredCandidate candidate : sorted) {
                if (selectedIds.add(candidate.book().getId())) selected.add(candidate);
                if (selected.size() == limit) break;
            }
        }
        List<RankedRecommendation> ranked = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            ScoredCandidate candidate = selected.get(index);
            ranked.add(new RankedRecommendation(index + 1, candidate.book(), candidate.total(),
                    candidate.content(), candidate.collaborative(), candidate.quality(),
                    candidate.exploration(), candidate.sourceType(), candidate.reason()));
        }
        return ranked;
    }

    private boolean same(String left, String right) {
        return !normalize(left).isEmpty() && normalize(left).equals(normalize(right));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private double decimal(Double value) {
        return value == null || !Double.isFinite(value) ? 0d : value;
    }

    public record RecommendationPlan(String mode,
                                     boolean personalized,
                                     int signalCount,
                                     List<RankedRecommendation> items) {
    }

    public record RankedRecommendation(int rank,
                                       RecommendationBookProfile book,
                                       double totalScore,
                                       double contentScore,
                                       double collaborativeScore,
                                       double qualityScore,
                                       double explorationScore,
                                       String sourceType,
                                       String reason) {
    }

    private record ScoredCandidate(RecommendationBookProfile book,
                                   double total,
                                   double content,
                                   double collaborative,
                                   double quality,
                                   double exploration,
                                   String sourceType,
                                   String reason) {
    }

    private record CollaborativeScores(Map<Integer, Double> scores,
                                       Map<Integer, Integer> sourceBookByCandidate) {
        private static CollaborativeScores empty() {
            return new CollaborativeScores(Map.of(), Map.of());
        }
    }
}
