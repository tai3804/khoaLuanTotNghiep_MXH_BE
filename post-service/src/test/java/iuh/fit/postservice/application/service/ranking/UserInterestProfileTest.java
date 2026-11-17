package iuh.fit.postservice.application.service.ranking;

import iuh.fit.postservice.application.service.ranking.ItemCollaborativeFilter.Interaction;
import iuh.fit.postservice.application.service.ranking.PostRelevanceScorer.Mode;
import iuh.fit.postservice.application.service.ranking.PostRelevanceScorer.Signals;
import iuh.fit.postservice.application.service.ranking.UserInterestProfile.Weighted;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserInterestProfileTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 12, 0);
    private final UUID viewer = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private static Interaction at(UUID user, UUID post, double weight, double ageDays) {
        return new Interaction(user, post, weight, NOW.minusMinutes((long) (ageDays * 1440)));
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    @Test
    void aggregateUsesOnlyViewerAndAppliesTypeWeightAndDecay() {
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID();
        List<Interaction> data = List.of(
                at(viewer, p1, ItemCollaborativeFilter.W_REACTION, 0),
                at(viewer, p1, ItemCollaborativeFilter.W_COMMENT, 0),    // cùng bài: cộng dồn 1 + 2 = 3
                at(viewer, p2, ItemCollaborativeFilter.W_REACTION, 30),  // sau đúng một nửa đời: 0,5
                at(other, p3, ItemCollaborativeFilter.W_SHARE, 0));      // của người khác: bị bỏ qua
        List<Weighted> agg = UserInterestProfile.aggregate(viewer, data, NOW);
        assertEquals(2, agg.size());
        assertEquals(p1, agg.get(0).postId());
        assertEquals(3.0, agg.get(0).weight(), 1e-9);
        assertEquals(0.5, agg.get(1).weight(), 1e-6);
    }

    @Test
    void aggregateKeepsOnlyHeaviestMaxItems() {
        List<Interaction> data = new ArrayList<>();
        for (int i = 0; i < UserInterestProfile.MAX_ITEMS + 10; i++) data.add(at(viewer, UUID.randomUUID(), 1.0, i));
        assertEquals(UserInterestProfile.MAX_ITEMS, UserInterestProfile.aggregate(viewer, data, NOW).size());
    }

    @Test
    void centroidPointsTowardHeavierTopicAndIsUnitLength() {
        UUID a1 = UUID.randomUUID(), a2 = UUID.randomUUID(), b1 = UUID.randomUUID();
        Map<UUID, float[]> emb = new HashMap<>();
        emb.put(a1, new float[]{1f, 0f, 0f});   // chủ đề A
        emb.put(a2, new float[]{0.9f, 0.1f, 0f});
        emb.put(b1, new float[]{0f, 1f, 0f});   // chủ đề B
        List<Weighted> items = List.of(new Weighted(a1, 3), new Weighted(a2, 2), new Weighted(b1, 1));
        float[] c = UserInterestProfile.centroid(items, emb).orElseThrow();
        assertEquals(1.0, Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]), 1e-5);
        assertTrue(cosine(c, new float[]{1, 0, 0}) > cosine(c, new float[]{0, 1, 0}), "trọng số lớn hơn thì gần chủ đề A hơn");
        assertTrue(cosine(c, new float[]{0, 0, 1}) < 1e-6);
    }

    @Test
    void coldStartWhenTooFewPostsHaveVectors() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        Map<UUID, float[]> emb = new HashMap<>();
        emb.put(a, new float[]{1f, 0f});
        emb.put(b, new float[]{0f, 1f});               // c không có vector
        Optional<float[]> r = UserInterestProfile.centroid(List.of(new Weighted(a, 1), new Weighted(b, 1), new Weighted(c, 1)), emb);
        assertTrue(r.isEmpty(), "chỉ 2 bài có vector, dưới ngưỡng " + UserInterestProfile.MIN_ITEMS);
    }

    @Test
    void boostIsZeroBelowFloorAndGrowsLinearlyToOne() {
        assertEquals(0.0, UserInterestProfile.boost(0.30), 1e-12);
        assertEquals(0.0, UserInterestProfile.boost(UserInterestProfile.SIM_FLOOR), 1e-12);
        assertEquals(0.5, UserInterestProfile.boost(0.725), 1e-9);   // (0,725 − 0,45) / 0,55
        assertEquals(1.0, UserInterestProfile.boost(1.0), 1e-12);
    }

    @Test
    void vectorStringMatchesPgvectorFormat() {
        assertEquals("[0.5,-1.0,2.0]", UserInterestProfile.toVectorString(new float[]{0.5f, -1f, 2f}));
    }

    @Test
    void scorerAddsContentBoostAndReason() {
        Signals without = new Signals(1, 0, 0, 0, false, 50, false, false, false, false, 0, false, 0.0, null, 0.0, 0.0);
        Signals with = new Signals(1, 0, 0, 0, false, 50, false, false, false, false, 0, false, 0.0, null, 0.0, 0.8);
        double base = PostRelevanceScorer.score(Mode.RELEVANCE, without).score();
        PostRelevanceScorer.Scored boosted = PostRelevanceScorer.score(Mode.RELEVANCE, with);
        assertTrue(boosted.score() > base);
        assertEquals("content", boosted.reason());
    }

    @Test
    void contentBoostIgnoredInTrendingMode() {
        Signals s0 = new Signals(1, 5, 1, 0, false, 50, false, false, false, false, 0, false, 0.0, null, 0.0, 0.0);
        Signals s1 = new Signals(1, 5, 1, 0, false, 50, false, false, false, false, 0, false, 0.0, null, 0.0, 1.0);
        assertEquals(PostRelevanceScorer.score(Mode.TRENDING, s0).score(), PostRelevanceScorer.score(Mode.TRENDING, s1).score(), 1e-12);
    }
}
