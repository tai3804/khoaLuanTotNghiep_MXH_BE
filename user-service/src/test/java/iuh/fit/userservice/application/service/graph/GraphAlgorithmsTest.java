package iuh.fit.userservice.application.service.graph;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphAlgorithmsTest {

    private static UUID id(int n) {
        return new UUID(0, n);
    }

    private static void edge(Map<UUID, Set<UUID>> g, int a, int b) {
        g.computeIfAbsent(id(a), k -> new HashSet<>()).add(id(b));
        g.computeIfAbsent(id(b), k -> new HashSet<>()).add(id(a));
    }

    // ---------- Personalized PageRank ----------

    @Test
    void closerNodesScoreHigherOnAPath() {
        Map<UUID, Set<UUID>> g = new HashMap<>();
        edge(g, 1, 2);
        edge(g, 2, 3);
        edge(g, 3, 4);
        Map<UUID, Double> p = PersonalizedPageRank.compute(g, id(1));
        assertTrue(p.get(id(2)) > p.get(id(3)));
        assertTrue(p.get(id(3)) > p.get(id(4)));
        assertFalse(p.containsKey(id(1)), "người xuất phát không nằm trong kết quả");
    }

    @Test
    void probabilityMassIsConserved() {
        Map<UUID, Set<UUID>> g = new HashMap<>();
        edge(g, 1, 2);
        edge(g, 1, 3);
        edge(g, 2, 3);
        edge(g, 3, 4);
        Map<UUID, Double> p = PersonalizedPageRank.compute(g, id(1), 0.15, 200, 1e-12);
        // Khối lượng của các nút khác nguồn + khối lượng của nguồn = 1; nguồn có tối thiểu α = 0,15
        double others = p.values().stream().mapToDouble(Double::doubleValue).sum();
        assertTrue(others < 1.0 && others > 0.0);
        assertTrue(1.0 - others >= 0.15 - 1e-9, "nguồn giữ ít nhất xác suất quay về α");
    }

    @Test
    void manyShortPathsBeatOneLongPath() {
        // 1 nối 2, 3, 4; cả 2, 3, 4 cùng nối tới 5 (ba đường ngắn). 1 – 6 – 7 – 8 là đường dài tới 8.
        Map<UUID, Set<UUID>> g = new HashMap<>();
        for (int m = 2; m <= 4; m++) {
            edge(g, 1, m);
            edge(g, m, 5);
        }
        edge(g, 1, 6);
        edge(g, 6, 7);
        edge(g, 7, 8);
        Map<UUID, Double> p = PersonalizedPageRank.compute(g, id(1));
        assertTrue(p.get(id(5)) > p.get(id(8)), "nhiều đường ngắn thắng một đường dài");
    }

    @Test
    void disconnectedNodesGetNoScoreAndIsolatedSourceYieldsNothing() {
        Map<UUID, Set<UUID>> g = new HashMap<>();
        edge(g, 1, 2);
        edge(g, 10, 11);
        Map<UUID, Double> p = PersonalizedPageRank.compute(g, id(1));
        assertFalse(p.containsKey(id(10)));
        assertFalse(p.containsKey(id(11)));
        assertTrue(PersonalizedPageRank.compute(g, id(99)).isEmpty());
    }

    @Test
    void degreeNormalisationDemotesHubs() {
        // 1 nối 2 và 3; 2 là "hub" nối thêm 10 người nữa, 3 chỉ nối tới 4. Điểm thô nghiêng về hub.
        Map<UUID, Set<UUID>> g = new HashMap<>();
        edge(g, 1, 2);
        edge(g, 1, 3);
        for (int i = 20; i < 30; i++) edge(g, 2, i);
        edge(g, 3, 4);
        Map<UUID, Double> raw = PersonalizedPageRank.compute(g, id(1));
        Map<UUID, Double> adjusted = PersonalizedPageRank.degreeNormalized(raw, g, PersonalizedPageRank.DEFAULT_DEGREE_EXPONENT);
        double ratioRaw = raw.get(id(2)) / raw.get(id(3));
        double ratioAdjusted = adjusted.get(id(2)) / adjusted.get(id(3));
        assertTrue(ratioAdjusted < ratioRaw, "chia cho deg^β làm giảm lợi thế của nút bậc lớn");
    }

    @Test
    void scaleToUnitMakesTheBestScoreOne() {
        Map<UUID, Double> scaled = PersonalizedPageRank.scaleToUnit(Map.of(id(1), 0.2, id(2), 0.5));
        assertEquals(1.0, scaled.get(id(2)), 1e-12);
        assertEquals(0.4, scaled.get(id(1)), 1e-12);
        assertTrue(PersonalizedPageRank.scaleToUnit(Map.of()).isEmpty());
    }

    // ---------- Label Propagation ----------

    @Test
    void twoCliquesJoinedByABridgeFormTwoCommunities() {
        Map<UUID, Set<UUID>> g = new HashMap<>();
        for (int i = 1; i <= 5; i++) for (int j = i + 1; j <= 5; j++) edge(g, i, j);          // nhóm A: 1..5
        for (int i = 11; i <= 15; i++) for (int j = i + 1; j <= 15; j++) edge(g, i, j);      // nhóm B: 11..15
        edge(g, 5, 11);                                                                       // cầu nối duy nhất

        Map<UUID, Integer> label = LabelPropagation.run(g, 42);
        for (int i = 2; i <= 5; i++) assertEquals(label.get(id(1)), label.get(id(i)));
        for (int i = 12; i <= 15; i++) assertEquals(label.get(id(11)), label.get(id(i)));
        assertNotEquals(label.get(id(1)), label.get(id(11)));
    }

    @Test
    void labelPropagationIsDeterministicForAGivenSeed() {
        Map<UUID, Set<UUID>> g = new HashMap<>();
        for (int i = 1; i <= 6; i++) for (int j = i + 1; j <= 6; j++) edge(g, i, j);
        edge(g, 6, 7);
        edge(g, 7, 8);
        assertEquals(LabelPropagation.run(g, 5), LabelPropagation.run(g, 5));
    }
}
