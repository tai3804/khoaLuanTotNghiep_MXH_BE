package iuh.fit.postservice.application.service.ranking;

import iuh.fit.postservice.application.service.ranking.PostRelevanceScorer.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Đa dạng hoá theo nội dung bằng MMR (Maximal Marginal Relevance, Carbonell &amp; Goldstein, SIGIR 1998).
 *
 * <p>Chọn lần lượt từng mục sao cho cân bằng giữa <i>độ liên quan</i> và <i>độ mới lạ so với những gì đã chọn</i>:
 * <pre>
 * MMR(i) = λ · điểm_chuẩn_hoá(i) − (1 − λ) · dư_thừa(i | S),    chọn i có MMR lớn nhất
 * dư_thừa(i | S) = max( max_{j∈S} sim(i, j),  dư_thừa_tác_giả(i, S) )
 * </pre>
 *
 * <h3>Hai điểm kỹ thuật</h3>
 * <ul>
 *   <li><b>Cosine thô không dùng trực tiếp được.</b> Embedding dồn cụm: hai bài không liên quan vẫn có cosine 0,55–0,63. Nếu coi
 *       cosine 0,6 là "trùng 60%" thì mọi bài đều bị phạt nặng như nhau. Ở đây sàn được ước lượng thích nghi bằng cosine trung
 *       bình giữa mọi cặp trong tập đang xét (μ), và {@code sim = clamp((cos − μ)/(1 − μ), 0, 1)}: bằng 0 với cặp "bình thường",
 *       bằng 1 với cặp trùng lặp.</li>
 *   <li><b>Giữ cả đa dạng theo tác giả</b> như {@link PostRelevanceScorer#diversify}: bài thứ k của cùng một tác giả có
 *       dư_thừa tối thiểu {@code min(1, 0,25 + 0,25·k)} (0,5 / 0,75 / 1,0 cho bài thứ 2 / 3 / 4 của một người), và bài thứ
 *       ba liên tiếp của một người bị phạt rất mạnh. Mức này không yếu hơn hệ số 0,75^k của cách cũ ở λ = 0,7.</li>
 * </ul>
 *
 * Bài chưa có vector chỉ chịu phạt theo tác giả. Chi phí O(limit · n · d) với n ứng viên, d = 768 chiều (n ≤ 120: vài chục ms).
 * Hàm thuần, không truy cập cơ sở dữ liệu.
 */
public final class MmrReranker {

    public static final double DEFAULT_LAMBDA = 0.70;
    private static final double MIN_FLOOR = 0.0;
    private static final double MAX_FLOOR = 0.95;
    private static final double DEFAULT_FLOOR = 0.60;
    private static final double CONSECUTIVE_REDUNDANCY = 0.90;

    private MmrReranker() {
    }

    /**
     * @param items   các mục đã chấm điểm (thứ tự đầu vào chỉ dùng để phá hoà)
     * @param vectors vector ngữ nghĩa theo mã bài; thiếu thì chỉ phạt theo tác giả
     * @param lambda  trọng số của độ liên quan (1 = bỏ qua đa dạng, 0 = chỉ đa dạng)
     * @param limit   số mục tối đa trả về
     */
    public static List<Item> rerank(List<Item> items, Map<UUID, float[]> vectors, double lambda, int limit) {
        int n = items.size();
        if (n == 0 || limit <= 0) return List.of();
        double lam = Math.min(1.0, Math.max(0.0, lambda));

        double maxScore = 0;
        for (Item it : items) maxScore = Math.max(maxScore, it.score());
        double[] rel = new double[n];
        for (int i = 0; i < n; i++) rel[i] = maxScore > 0 ? Math.max(0, items.get(i).score()) / maxScore : 0;

        float[][] unit = new float[n][];
        int withVector = 0;
        for (int i = 0; i < n; i++) {
            float[] v = vectors == null ? null : vectors.get(items.get(i).postId());
            unit[i] = normalize(v);
            if (unit[i] != null) withVector++;
        }
        double floor = withVector >= 3 ? meanPairwiseCosine(unit) : DEFAULT_FLOOR;
        floor = Math.min(MAX_FLOOR, Math.max(MIN_FLOOR, floor));

        double[] maxSim = new double[n];
        boolean[] picked = new boolean[n];
        Map<UUID, Integer> perAuthor = new HashMap<>();
        List<Item> out = new ArrayList<>(Math.min(limit, n));

        while (out.size() < limit && out.size() < n) {
            UUID last1 = out.size() >= 1 ? out.get(out.size() - 1).authorId() : null;
            UUID last2 = out.size() >= 2 ? out.get(out.size() - 2).authorId() : null;

            int best = -1;
            double bestValue = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                if (picked[i]) continue;
                Item it = items.get(i);
                int k = perAuthor.getOrDefault(it.authorId(), 0);
                double redundancy = Math.max(maxSim[i], authorRedundancy(k));
                if (it.authorId() != null && it.authorId().equals(last1) && it.authorId().equals(last2)) {
                    redundancy = Math.max(redundancy, CONSECUTIVE_REDUNDANCY);
                }
                double value = lam * rel[i] - (1.0 - lam) * redundancy;
                if (value > bestValue) {
                    bestValue = value;
                    best = i;
                }
            }
            picked[best] = true;
            Item chosen = items.get(best);
            out.add(chosen);
            perAuthor.merge(chosen.authorId(), 1, Integer::sum);

            if (unit[best] != null) {
                for (int i = 0; i < n; i++) {
                    if (picked[i] || unit[i] == null) continue;
                    double sim = Math.min(1.0, Math.max(0.0, (dot(unit[best], unit[i]) - floor) / (1.0 - floor)));
                    if (sim > maxSim[i]) maxSim[i] = sim;
                }
            }
        }
        return out;
    }

    /** Dư thừa do trùng tác giả: bài thứ (k + 1) của cùng một người, k bài đã được chọn trước đó. */
    static double authorRedundancy(int alreadyPicked) {
        return alreadyPicked <= 0 ? 0.0 : Math.min(1.0, 0.25 + 0.25 * alreadyPicked);
    }

    /** Cosine trung bình giữa mọi cặp có vector: ước lượng "mức giống nhau nền" của tập đang xét. */
    static double meanPairwiseCosine(float[][] unit) {
        double sum = 0;
        long count = 0;
        for (int i = 0; i < unit.length; i++) {
            if (unit[i] == null) continue;
            for (int j = i + 1; j < unit.length; j++) {
                if (unit[j] == null) continue;
                sum += dot(unit[i], unit[j]);
                count++;
            }
        }
        return count == 0 ? DEFAULT_FLOOR : sum / count;
    }

    private static float[] normalize(float[] v) {
        if (v == null || v.length == 0) return null;
        double s = 0;
        for (float x : v) s += (double) x * x;
        double norm = Math.sqrt(s);
        if (norm == 0) return null;
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / norm);
        return out;
    }

    private static double dot(float[] a, float[] b) {
        int len = Math.min(a.length, b.length);
        double s = 0;
        for (int i = 0; i < len; i++) s += (double) a[i] * b[i];
        return s;
    }
}
