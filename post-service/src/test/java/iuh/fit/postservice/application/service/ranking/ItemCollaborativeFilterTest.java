package iuh.fit.postservice.application.service.ranking;

import iuh.fit.postservice.application.service.ranking.ItemCollaborativeFilter.Interaction;
import iuh.fit.postservice.application.service.ranking.PostRelevanceScorer.Mode;
import iuh.fit.postservice.application.service.ranking.PostRelevanceScorer.Signals;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemCollaborativeFilterTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 12, 0);
    private final UUID viewer = UUID.randomUUID();
    private final UUID u1 = UUID.randomUUID();
    private final UUID u2 = UUID.randomUUID();
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final UUID d = UUID.randomUUID();

    private static Interaction like(UUID user, UUID post, double ageDays) {
        return new Interaction(user, post, ItemCollaborativeFilter.W_REACTION, NOW.minusMinutes((long) (ageDays * 1440)));
    }

    @Test
    void candidateCoEngagedWithViewerItemsGetsPositiveScore() {
        List<Interaction> data = new ArrayList<>(List.of(
                like(viewer, a, 1), like(viewer, b, 1),
                like(u1, a, 1), like(u1, b, 1), like(u1, c, 1),
                like(u2, a, 1), like(u2, c, 1)));
        Map<UUID, Double> scores = ItemCollaborativeFilter.score(viewer, List.of(c, d), data, NOW);
        assertTrue(scores.get(c) > 0, "bài c có người chung sở thích cùng tương tác");
        assertTrue(scores.get(c) <= 1.0);
        assertFalse(scores.containsKey(d), "bài d không ai cùng tương tác với bài của người xem");
    }

    @Test
    void coldStartViewerGetsNoCollaborativeScore() {
        List<Interaction> data = List.of(like(viewer, a, 1), like(u1, a, 1), like(u1, c, 1));
        assertTrue(ItemCollaborativeFilter.score(viewer, List.of(c), data, NOW).isEmpty());
    }

    @Test
    void shrinkageLowersSimilarityWhenOnlyOneCommonUser() {
        // Một người chung: cosine tối đa 1, nhưng sau co chỉ còn 1/(1+2)
        List<Interaction> one = List.of(
                like(viewer, a, 0), like(viewer, b, 0),
                like(u1, a, 0), like(u1, c, 0));
        double single = ItemCollaborativeFilter.score(viewer, List.of(c), one, NOW).get(c);
        assertTrue(single < 0.5, "một người chung không được coi là giống tuyệt đối, được " + single);

        // Hai người chung: cùng cosine nhưng số người chung nhiều hơn nên độ giống cao hơn
        List<Interaction> two = List.of(
                like(viewer, a, 0), like(viewer, b, 0),
                like(u1, a, 0), like(u1, c, 0),
                like(u2, a, 0), like(u2, c, 0));
        double both = ItemCollaborativeFilter.score(viewer, List.of(c), two, NOW).get(c);
        assertTrue(both > single);
    }

    @Test
    void olderInteractionsContributeLess() {
        List<Interaction> fresh = List.of(
                like(viewer, a, 0), like(viewer, b, 0), like(u1, a, 0), like(u1, c, 0));
        List<Interaction> stale = List.of(
                like(viewer, a, 0), like(viewer, b, 0), like(u1, a, 0), like(u1, c, 0),
                like(u2, c, 0));
        // Thay tương tác của u1 bằng bản cũ 60 ngày: độ giống không được tăng, và điểm vẫn nằm trong [0, 1]
        List<Interaction> old = List.of(
                like(viewer, a, 0), like(viewer, b, 0), like(u1, a, 60), like(u1, c, 60));
        double now = ItemCollaborativeFilter.score(viewer, List.of(c), fresh, NOW).get(c);
        double later = ItemCollaborativeFilter.score(viewer, List.of(c), old, NOW).get(c);
        assertEquals(now, later, 1e-9, "cosine không đổi khi cả hai bài cùng suy giảm như nhau");
        assertTrue(ItemCollaborativeFilter.score(viewer, List.of(c), stale, NOW).get(c) >= 0);
    }

    @Test
    void viewerOwnInteractionsDoNotCreateSimilarity() {
        // Chỉ người xem tương tác cả a, b, c: không có người dùng khác để suy ra độ giống
        List<Interaction> data = List.of(like(viewer, a, 0), like(viewer, b, 0), like(viewer, c, 0));
        assertTrue(ItemCollaborativeFilter.score(viewer, List.of(c), data, NOW).isEmpty());
    }

    @Test
    void scorerAddsCollaborativeBoostAndReason() {
        Signals without = new Signals(1, 0, 0, 0, false, 50, false, false, false, false, 0, false, 0.0, null, 0.0);
        Signals with = new Signals(1, 0, 0, 0, false, 50, false, false, false, false, 0, false, 0.0, null, 0.8);
        double base = PostRelevanceScorer.score(Mode.RELEVANCE, without).score();
        PostRelevanceScorer.Scored boosted = PostRelevanceScorer.score(Mode.RELEVANCE, with);
        assertTrue(boosted.score() > base);
        assertEquals("collaborative", boosted.reason());
    }

    @Test
    void collaborativeSignalIgnoredInTrendingMode() {
        Signals s0 = new Signals(1, 5, 1, 0, false, 50, false, false, false, false, 0, false, 0.0, null, 0.0);
        Signals s1 = new Signals(1, 5, 1, 0, false, 50, false, false, false, false, 0, false, 0.0, null, 1.0);
        assertEquals(PostRelevanceScorer.score(Mode.TRENDING, s0).score(), PostRelevanceScorer.score(Mode.TRENDING, s1).score(), 1e-12);
    }
}
