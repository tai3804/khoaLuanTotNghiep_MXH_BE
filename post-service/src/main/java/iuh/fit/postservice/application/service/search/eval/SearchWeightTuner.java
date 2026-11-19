package iuh.fit.postservice.application.service.search.eval;

import iuh.fit.postservice.application.service.search.SearchScorer.Weights;
import iuh.fit.postservice.application.service.search.eval.SearchEvaluator.LabeledQuery;
import iuh.fit.postservice.application.service.search.eval.SearchEvaluator.Metrics;

import java.util.ArrayList;
import java.util.List;

/**
 * Học bộ trọng số của hàm xếp hạng tìm kiếm bằng <b>tìm kiếm theo toạ độ</b> (coordinate ascent): lần lượt thử tăng/giảm từng
 * trong 7 trọng số, giữ thay đổi nếu làm tăng hàm mục tiêu, rồi thu nhỏ bước cho đến khi không còn cải thiện.
 *
 * <pre>
 * mục_tiêu(w) = NDCG@k trung bình trên tập huấn luyện  −  λ · ‖w_chuẩn_hoá − w_mặc_định‖²
 * </pre>
 *
 * <ul>
 *   <li>NDCG@k là hàm hằng từng khúc theo trọng số nên không dùng được gradient; toạ độ + bước giảm dần đủ cho 7 tham số và
 *       không cần thư viện tối ưu.</li>
 *   <li>Số hạng điều chuẩn λ (L2 kéo về trọng số mặc định) chống "học thuộc" khi tập nhãn nhỏ.</li>
 *   <li>Tất định: cùng dữ liệu cho cùng kết quả.</li>
 * </ul>
 */
public final class SearchWeightTuner {

    static final double LAMBDA = 0.02;
    static final double[] STEPS = {0.30, 0.15, 0.08, 0.04, 0.02};
    static final int MAX_SWEEPS = 25;

    private SearchWeightTuner() {
    }

    public record Result(Weights weights, double trainNdcg, double defaultNdcg, int evaluations) {
    }

    public static Result tune(List<LabeledQuery> train, int k) {
        double[] best = Weights.DEFAULT.toArray();
        double bestObjective = objective(Weights.fromArray(best), train, k);
        int evaluations = 1;

        for (double step : STEPS) {
            for (int sweep = 0; sweep < MAX_SWEEPS; sweep++) {
                boolean improved = false;
                for (int j = 0; j < Weights.SIZE; j++) {
                    for (double delta : new double[]{step, -step}) {
                        double[] trial = best.clone();
                        trial[j] = Math.max(0, trial[j] + delta);
                        if (j == Weights.SIZE - 1) trial[j] = Math.min(Weights.MAX_SPAN, trial[j]);
                        double value = objective(Weights.fromArray(trial), train, k);
                        evaluations++;
                        if (value > bestObjective + 1e-9) {
                            best = trial;
                            bestObjective = value;
                            improved = true;
                        }
                    }
                }
                if (!improved) break;
            }
        }
        Weights tuned = Weights.fromArray(best).normalized();
        return new Result(tuned, SearchEvaluator.meanNdcg(train, tuned, k),
                SearchEvaluator.meanNdcg(train, Weights.DEFAULT, k), evaluations);
    }

    static double objective(Weights w, List<LabeledQuery> train, int k) {
        double[] n = w.normalized().toArray();
        double[] d = Weights.DEFAULT.normalized().toArray();
        double penalty = 0;
        for (int i = 0; i < n.length; i++) penalty += (n[i] - d[i]) * (n[i] - d[i]);
        return SearchEvaluator.meanNdcg(train, w, k) - LAMBDA * penalty;
    }

    /**
     * Kiểm định chéo: chia truy vấn thành {@code folds} phần (truy vấn thứ i vào phần i mod folds), học trên các phần còn lại,
     * chấm trên phần đó rồi lấy trung bình có trọng số theo số truy vấn của mỗi phần.
     */
    public static Metrics crossValidate(List<LabeledQuery> queries, int k, int folds) {
        if (queries.size() < 2) return SearchEvaluator.evaluate(queries, Weights.DEFAULT, k);
        int f = Math.max(2, Math.min(folds, queries.size()));

        double p = 0, r = 0, ap = 0, rr = 0, ndcg = 0;
        int total = 0;
        for (int fold = 0; fold < f; fold++) {
            List<LabeledQuery> train = new ArrayList<>();
            List<LabeledQuery> test = new ArrayList<>();
            for (int i = 0; i < queries.size(); i++) {
                if (i % f == fold) test.add(queries.get(i));
                else train.add(queries.get(i));
            }
            if (test.isEmpty() || train.isEmpty()) continue;
            Weights tuned = tune(train, k).weights();
            Metrics m = SearchEvaluator.evaluate(test, tuned, k);
            int n = test.size();
            p += m.precision() * n;
            r += m.recall() * n;
            ap += m.map() * n;
            rr += m.mrr() * n;
            ndcg += m.ndcg() * n;
            total += n;
        }
        return total == 0 ? new Metrics(0, k, 0, 0, 0, 0, 0)
                : new Metrics(total, k, p / total, r / total, ap / total, rr / total, ndcg / total);
    }
}
