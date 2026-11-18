package iuh.fit.postservice.application.service.ranking;

import iuh.fit.postservice.application.service.ranking.PostRelevanceScorer.Item;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MmrRerankerTest {

    private static float[] v(double... x) {
        float[] f = new float[x.length];
        for (int i = 0; i < x.length; i++) f[i] = (float) x[i];
        return f;
    }

    private static Item item(UUID id, UUID author, double score) {
        return new Item(id, author, score, "r");
    }

    @Test
    void nearDuplicatesAreSeparatedByADifferentTopic() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
        UUID u1 = UUID.randomUUID(), u2 = UUID.randomUUID(), u3 = UUID.randomUUID(), u4 = UUID.randomUUID();
        // a, b, c cùng chủ đề (gần như trùng nhau); d khác chủ đề và điểm thấp hơn
        Map<UUID, float[]> vectors = new HashMap<>();
        vectors.put(a, v(1, 0.00, 0));
        vectors.put(b, v(1, 0.02, 0));
        vectors.put(c, v(1, 0.01, 0.01));
        vectors.put(d, v(0, 1, 0));
        List<Item> items = List.of(item(a, u1, 1.0), item(b, u2, 0.95), item(c, u3, 0.90), item(d, u4, 0.60));

        List<Item> out = MmrReranker.rerank(items, vectors, MmrReranker.DEFAULT_LAMBDA, 4);
        assertEquals(a, out.get(0).postId(), "bài điểm cao nhất luôn đứng đầu");
        assertEquals(d, out.get(1).postId(), "bài khác chủ đề được đưa lên trước hai bài gần như trùng với bài đầu");
        assertEquals(4, out.size());
    }

    @Test
    void lambdaOneKeepsPlainScoreOrder() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        Map<UUID, float[]> vectors = Map.of(a, v(1, 0), b, v(1, 0.01), c, v(0, 1));
        List<Item> items = List.of(item(a, UUID.randomUUID(), 1.0), item(b, UUID.randomUUID(), 0.9), item(c, UUID.randomUUID(), 0.5));
        List<Item> out = MmrReranker.rerank(items, vectors, 1.0, 3);
        assertEquals(List.of(a, b, c), out.stream().map(Item::postId).toList());
    }

    @Test
    void withoutVectorsItStillSpreadsAuthors() {
        UUID same = UUID.randomUUID(), other = UUID.randomUUID();
        UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID(), p4 = UUID.randomUUID();
        List<Item> items = List.of(item(p1, same, 1.0), item(p2, same, 0.95), item(p3, same, 0.93), item(p4, other, 0.80));
        List<Item> out = MmrReranker.rerank(items, Map.of(), MmrReranker.DEFAULT_LAMBDA, 4);
        assertEquals(p1, out.get(0).postId());
        assertEquals(p4, out.get(1).postId(), "tác giả khác được xen vào sau bài đầu của một người đăng dồn dập");
    }

    @Test
    void respectsLimitAndHandlesEmptyInput() {
        assertTrue(MmrReranker.rerank(List.of(), Map.of(), 0.7, 5).isEmpty());
        List<Item> items = List.of(item(UUID.randomUUID(), UUID.randomUUID(), 1), item(UUID.randomUUID(), UUID.randomUUID(), 0.5),
                item(UUID.randomUUID(), UUID.randomUUID(), 0.2));
        assertEquals(2, MmrReranker.rerank(items, Map.of(), 0.7, 2).size());
    }

    @Test
    void floorIsAdaptiveSoUniformlySimilarSetsAreNotAllPenalisedEqually() {
        // Mọi bài đều giống nhau 0,6 (nền cao): sàn thích nghi ≈ 0,6 nên không bài nào bị phạt nặng, thứ tự theo điểm
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        Map<UUID, float[]> vectors = new HashMap<>();
        // ba vector độ dài 1, mọi cặp có tích vô hướng đúng 0,6
        double s = Math.sqrt(0.6), t = Math.sqrt(0.4);
        vectors.put(a, v(s, t, 0, 0));
        vectors.put(b, v(s, 0, t, 0));
        vectors.put(c, v(s, 0, 0, t));
        List<Item> items = List.of(item(a, UUID.randomUUID(), 1.0), item(b, UUID.randomUUID(), 0.9), item(c, UUID.randomUUID(), 0.8));
        List<Item> out = MmrReranker.rerank(items, vectors, MmrReranker.DEFAULT_LAMBDA, 3);
        assertEquals(List.of(a, b, c), out.stream().map(Item::postId).toList());
    }
}
