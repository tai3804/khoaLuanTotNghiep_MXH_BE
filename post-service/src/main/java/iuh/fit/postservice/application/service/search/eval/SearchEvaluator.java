package iuh.fit.postservice.application.service.search.eval;

import iuh.fit.postservice.application.service.search.SearchScorer;
import iuh.fit.postservice.application.service.search.SearchScorer.Weights;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Đánh giá offline các phương án xếp hạng tìm kiếm trên cùng một tập truy vấn có gán nhãn (ablation study).
 *
 * <p>Mỗi truy vấn mang theo danh sách ứng viên đã được truy xuất (cùng đặc trưng) và bảng nhãn do người đánh giá gán. Vì ứng viên
 * và đặc trưng được "đóng băng", mọi phương án chỉ khác nhau ở cách chấm điểm nên so sánh là công bằng.
 *
 * <h3>Các phương án</h3>
 * <ol>
 *   <li><b>Chỉ RRF</b>: thứ tự sau khi hợp nhất các nhánh truy xuất, chưa xếp hạng lại;</li>
 *   <li><b>Chỉ ngữ nghĩa</b>: xếp theo điểm ngữ nghĩa (z-score của cosine);</li>
 *   <li><b>Ngữ nghĩa + cụm + hashtag</b>: relevance, chưa có tín hiệu xã hội;</li>
 *   <li><b>Đầy đủ (trọng số mặc định)</b>: relevance điều chỉnh bởi xã hội, tương tác, độ mới, uy tín;</li>
 *   <li><b>Đã học (kiểm định chéo)</b>: trọng số tối ưu trên các fold huấn luyện, đo trên fold chưa thấy.</li>
 * </ol>
 */
public final class SearchEvaluator {

    private SearchEvaluator() {
    }

    /** Một ứng viên đã truy xuất kèm đặc trưng. {@code rrfScore} là điểm hợp nhất RRF của bước truy xuất. */
    public record EvalCandidate(UUID postId, SearchScorer.Features features, double authorWeight, double rrfScore) {
    }

    public record LabeledQuery(String query, Map<UUID, Integer> grades, List<EvalCandidate> candidates) {
    }

    /** Trung bình trên các truy vấn. */
    public record Metrics(int queries, int k, double precision, double recall, double map, double mrr, double ndcg) {
    }

    public record VariantResult(String name, Metrics metrics) {
    }

    public static final Weights SEMANTIC_ONLY = new Weights(1, 0, 0, 0, 0, 0, 0);
    public static final Weights RELEVANCE_ONLY = new Weights(0.70, 0.20, 0.10, 0, 0, 0, 0);

    /** Thứ tự xếp hạng của các ứng viên theo bộ trọng số (điểm bằng nhau thì theo RRF). */
    public static List<UUID> rank(List<EvalCandidate> candidates, Weights weights) {
        return candidates.stream()
                .sorted(Comparator
                        .comparingDouble((EvalCandidate c) -> SearchScorer.score(c.features(), c.authorWeight(), weights)).reversed()
                        .thenComparing(Comparator.comparingDouble(EvalCandidate::rrfScore).reversed()))
                .map(EvalCandidate::postId).toList();
    }

    public static List<UUID> rankByRrf(List<EvalCandidate> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparingDouble(EvalCandidate::rrfScore).reversed())
                .map(EvalCandidate::postId).toList();
    }

    public static Metrics evaluate(List<LabeledQuery> queries, Weights weights, int k) {
        return aggregate(queries, q -> rank(q.candidates(), weights), k);
    }

    public static Metrics evaluateRrf(List<LabeledQuery> queries, int k) {
        return aggregate(queries, q -> rankByRrf(q.candidates()), k);
    }

    /** NDCG@k trung bình: hàm mục tiêu của bộ học trọng số. */
    public static double meanNdcg(List<LabeledQuery> queries, Weights weights, int k) {
        if (queries.isEmpty()) return 0;
        double sum = 0;
        for (LabeledQuery q : queries) sum += RankingMetrics.ndcgAtK(rank(q.candidates(), weights), q.grades(), k);
        return sum / queries.size();
    }

    private interface Ranker {
        List<UUID> rank(LabeledQuery q);
    }

    private static Metrics aggregate(List<LabeledQuery> queries, Ranker ranker, int k) {
        if (queries.isEmpty()) return new Metrics(0, k, 0, 0, 0, 0, 0);
        double p = 0, r = 0, ap = 0, rr = 0, ndcg = 0;
        for (LabeledQuery q : queries) {
            List<UUID> ranked = ranker.rank(q);
            p += RankingMetrics.precisionAtK(ranked, q.grades(), k);
            r += RankingMetrics.recallAtK(ranked, q.grades(), k);
            ap += RankingMetrics.averagePrecision(ranked, q.grades());
            rr += RankingMetrics.reciprocalRank(ranked, q.grades());
            ndcg += RankingMetrics.ndcgAtK(ranked, q.grades(), k);
        }
        int n = queries.size();
        return new Metrics(n, k, p / n, r / n, ap / n, rr / n, ndcg / n);
    }

    /**
     * Báo cáo so sánh các phương án. Phương án "đã học" dùng kiểm định chéo {@code folds} phần: với mỗi phần, học trọng số trên
     * các phần còn lại rồi chấm trên phần đó, nên không bị "học thuộc" tập đánh giá.
     */
    public static List<VariantResult> report(List<LabeledQuery> queries, int k, int folds) {
        List<VariantResult> out = new ArrayList<>();
        out.add(new VariantResult("Chỉ RRF (trước xếp hạng lại)", evaluateRrf(queries, k)));
        out.add(new VariantResult("Chỉ ngữ nghĩa", evaluate(queries, SEMANTIC_ONLY, k)));
        out.add(new VariantResult("Ngữ nghĩa + cụm + hashtag", evaluate(queries, RELEVANCE_ONLY, k)));
        out.add(new VariantResult("Đầy đủ (trọng số mặc định)", evaluate(queries, Weights.DEFAULT, k)));
        out.add(new VariantResult("Đã học (kiểm định chéo " + folds + " phần)", SearchWeightTuner.crossValidate(queries, k, folds)));
        return out;
    }
}
