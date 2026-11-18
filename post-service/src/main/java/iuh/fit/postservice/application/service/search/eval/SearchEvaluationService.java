package iuh.fit.postservice.application.service.search.eval;

import iuh.fit.postservice.application.service.search.PostSearchService;
import iuh.fit.postservice.application.service.search.eval.SearchEvaluator.EvalCandidate;
import iuh.fit.postservice.application.service.search.eval.SearchEvaluator.LabeledQuery;
import iuh.fit.postservice.application.service.search.eval.SearchEvaluator.VariantResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chạy bộ đánh giá offline trên dữ liệu thật: với mỗi truy vấn đã gán nhãn, chạy giai đoạn truy xuất + trích đặc trưng của
 * {@link PostSearchService} (người xem ẩn danh nên chỉ thấy bài công khai), rồi so sánh các phương án xếp hạng và học bộ trọng số.
 */
@Service
@RequiredArgsConstructor
public class SearchEvaluationService {

    private final PostSearchService postSearchService;

    public record Judgment(UUID postId, int grade) {
    }

    public record JudgedQueryRequest(String query, List<Judgment> judgments) {
    }

    public record EvalRequest(Integer k, Integer folds, List<JudgedQueryRequest> queries) {
    }

    /** Chẩn đoán từng truy vấn: tầng truy xuất có tìm thấy các bài liên quan đã gán nhãn hay không. */
    public record QueryDiagnostic(String query, int candidates, int relevantLabelled, int relevantRetrieved,
                                  double retrievalRecall) {
    }

    public record Report(int queries, int k, int folds, List<VariantResult> variants,
                         double defaultNdcgOnAll, double tunedNdcgOnAll, String recommendedWeights,
                         List<QueryDiagnostic> diagnostics) {
    }

    public Report evaluate(EvalRequest request) {
        int k = request.k() == null || request.k() <= 0 ? 10 : Math.min(request.k(), 50);
        int folds = request.folds() == null || request.folds() < 2 ? 3 : Math.min(request.folds(), 10);

        List<LabeledQuery> labeled = new ArrayList<>();
        List<QueryDiagnostic> diagnostics = new ArrayList<>();
        for (JudgedQueryRequest jq : request.queries() == null ? List.<JudgedQueryRequest>of() : request.queries()) {
            if (jq.query() == null || jq.query().isBlank() || jq.judgments() == null) continue;
            Map<UUID, Integer> grades = new HashMap<>();
            for (Judgment j : jq.judgments()) grades.put(j.postId(), Math.max(0, j.grade()));

            PostSearchService.Collected collected = postSearchService.collect(jq.query(), null);
            List<EvalCandidate> candidates = collected.candidates().stream()
                    .map(c -> new EvalCandidate(c.post().getId(), c.features(), c.authorWeight(), c.rrfScore()))
                    .toList();
            labeled.add(new LabeledQuery(jq.query(), grades, candidates));

            long relevant = grades.values().stream().filter(g -> g > 0).count();
            long retrieved = candidates.stream().filter(c -> grades.getOrDefault(c.postId(), 0) > 0).count();
            diagnostics.add(new QueryDiagnostic(jq.query(), candidates.size(), (int) relevant, (int) retrieved,
                    relevant == 0 ? 0 : (double) retrieved / relevant));
        }

        List<VariantResult> variants = SearchEvaluator.report(labeled, k, folds);
        SearchWeightTuner.Result tuned = SearchWeightTuner.tune(labeled, k);
        return new Report(labeled.size(), k, folds, variants, tuned.defaultNdcg(), tuned.trainNdcg(),
                tuned.weights().toCsv(), diagnostics);
    }
}
