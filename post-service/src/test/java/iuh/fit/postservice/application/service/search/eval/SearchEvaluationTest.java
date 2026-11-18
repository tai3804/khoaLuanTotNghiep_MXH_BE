package iuh.fit.postservice.application.service.search.eval;

import iuh.fit.postservice.application.service.search.SearchScorer;
import iuh.fit.postservice.application.service.search.SearchScorer.Weights;
import iuh.fit.postservice.application.service.search.eval.SearchEvaluator.EvalCandidate;
import iuh.fit.postservice.application.service.search.eval.SearchEvaluator.LabeledQuery;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchEvaluationTest {

    private static List<UUID> ids(int n) {
        List<UUID> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new UUID(0, i + 1));
        return out;
    }

    // ---------- RankingMetrics: các giá trị tính tay ----------

    @Test
    void precisionRecallAndAveragePrecisionMatchHandComputedValues() {
        List<UUID> d = ids(5);
        Map<UUID, Integer> grades = Map.of(d.get(0), 1, d.get(2), 1, d.get(4), 1);   // 3 tài liệu liên quan
        List<UUID> ranked = d;                                                       // liên quan ở vị trí 1, 3, 5

        assertEquals(2.0 / 3, RankingMetrics.precisionAtK(ranked, grades, 3), 1e-12);
        assertEquals(2.0 / 3, RankingMetrics.recallAtK(ranked, grades, 3), 1e-12);
        // AP = (1/1 + 2/3 + 3/5) / 3
        assertEquals((1.0 + 2.0 / 3 + 3.0 / 5) / 3, RankingMetrics.averagePrecision(ranked, grades), 1e-12);
        assertEquals(1.0, RankingMetrics.reciprocalRank(ranked, grades), 1e-12);
        assertEquals(1.0 / 2, RankingMetrics.reciprocalRank(List.of(d.get(1), d.get(0)), grades), 1e-12);
        assertEquals(0.0, RankingMetrics.reciprocalRank(List.of(d.get(1), d.get(3)), grades), 1e-12);
    }

    @Test
    void ndcgIsOneForTheIdealOrderingAndLowerOtherwise() {
        List<UUID> d = ids(3);
        Map<UUID, Integer> grades = Map.of(d.get(0), 3, d.get(1), 2, d.get(2), 0);
        assertEquals(1.0, RankingMetrics.ndcgAtK(List.of(d.get(0), d.get(1), d.get(2)), grades, 3), 1e-12);

        // DCG của thứ tự (1, 0, 2): (2^2−1)/log2(2) + (2^3−1)/log2(3) ; IDCG: (2^3−1)/log2(2) + (2^2−1)/log2(3)
        double dcg = 3.0 / 1.0 + 7.0 / (Math.log(3) / Math.log(2));
        double idcg = 7.0 / 1.0 + 3.0 / (Math.log(3) / Math.log(2));
        assertEquals(dcg / idcg, RankingMetrics.ndcgAtK(List.of(d.get(1), d.get(0), d.get(2)), grades, 3), 1e-12);
        assertEquals(0.0, RankingMetrics.ndcgAtK(List.of(d.get(2)), grades, 3), 1e-12);
        assertEquals(0.0, RankingMetrics.ndcgAtK(List.of(d.get(0)), Map.of(d.get(0), 0), 3), 1e-12, "không có tài liệu liên quan");
    }

    // ---------- Dữ liệu tổng hợp: nhãn "thật" phụ thuộc mạnh vào cụm khớp, không phụ thuộc xã hội ----------

    /**
     * Mỗi truy vấn có 12 ứng viên. Bài thật sự liên quan có cụm khớp cao; ứng viên gây nhiễu có ngữ nghĩa cao hơn một chút nhưng
     * không khớp cụm, và có tín hiệu xã hội/độ mới cao (nhiễu).
     */
    private static List<LabeledQuery> syntheticQueries(int n, long seed) {
        Random rnd = new Random(seed);
        List<LabeledQuery> out = new ArrayList<>();
        for (int q = 0; q < n; q++) {
            List<EvalCandidate> candidates = new ArrayList<>();
            Map<UUID, Integer> grades = new HashMap<>();
            for (int i = 0; i < 12; i++) {
                UUID id = new UUID(q, i + 1);
                boolean relevant = i < 4;
                double z = relevant ? 3.0 + rnd.nextDouble() * 2.0 : 3.0 + rnd.nextDouble() * 3.0;
                double phrase = relevant ? 1.0 : (rnd.nextDouble() < 0.15 ? 0.6 : 0.0);
                double social = relevant ? rnd.nextDouble() * 0.3 : 0.5 + rnd.nextDouble() * 0.5;
                double fresh = relevant ? rnd.nextDouble() * 0.5 : 0.5 + rnd.nextDouble() * 0.5;
                candidates.add(new EvalCandidate(id, new SearchScorer.Features(z, phrase, 0, social, 0.3, fresh), 1.0, rnd.nextDouble() * 0.03));
                if (relevant) grades.put(id, 2);
            }
            out.add(new LabeledQuery("q" + q, grades, candidates));
        }
        return out;
    }

    @Test
    void learnedWeightsImproveNdcgOverTheDefaultsOnTrainingData() {
        List<LabeledQuery> queries = syntheticQueries(12, 7);
        double before = SearchEvaluator.meanNdcg(queries, Weights.DEFAULT, 5);
        SearchWeightTuner.Result tuned = SearchWeightTuner.tune(queries, 5);
        assertEquals(before, tuned.defaultNdcg(), 1e-12);
        assertTrue(tuned.trainNdcg() > before + 0.05,
                "học trọng số phải cải thiện rõ rệt: " + before + " -> " + tuned.trainNdcg());
        // Bộ trọng số học được nghiêng về cụm khớp và giảm tín hiệu xã hội/độ mới vốn là nhiễu trong dữ liệu này
        assertTrue(tuned.weights().phrase() > Weights.DEFAULT.normalized().phrase());
        assertTrue(tuned.weights().span() <= Weights.DEFAULT.span());
    }

    @Test
    void crossValidationEstimateIsMeasuredOnUnseenQueries() {
        List<LabeledQuery> queries = syntheticQueries(18, 11);
        SearchEvaluator.Metrics defaults = SearchEvaluator.evaluate(queries, Weights.DEFAULT, 5);
        SearchEvaluator.Metrics cv = SearchWeightTuner.crossValidate(queries, 5, 3);
        assertEquals(18, cv.queries());
        assertTrue(cv.ndcg() >= defaults.ndcg(), "trên dữ liệu đồng nhất, trọng số học được không kém mặc định khi chấm trên fold chưa thấy");
    }

    @Test
    void tunerIsDeterministic() {
        List<LabeledQuery> queries = syntheticQueries(8, 3);
        assertEquals(SearchWeightTuner.tune(queries, 5).weights(), SearchWeightTuner.tune(queries, 5).weights());
    }

    @Test
    void reportListsEveryVariantInOrder() {
        List<SearchEvaluator.VariantResult> report = SearchEvaluator.report(syntheticQueries(9, 5), 5, 3);
        assertEquals(5, report.size());
        assertTrue(report.get(0).name().contains("RRF"));
        assertTrue(report.get(4).name().contains("kiểm định chéo"));
        // chỉ ngữ nghĩa (nhiễu có ngữ nghĩa cao) kém hơn khi có thêm tín hiệu cụm khớp
        assertTrue(report.get(2).metrics().ndcg() > report.get(1).metrics().ndcg());
    }
}
