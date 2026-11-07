package iuh.fit.postservice.application.service.search;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reciprocal Rank Fusion (Cormack, Clarke &amp; Büttcher, SIGIR 2009) để trộn nhiều danh sách ứng viên có thang điểm
 * không so sánh được với nhau (BM25 và cosine) mà không cần chuẩn hoá điểm:
 *
 * <pre>RRF(d) = Σ_{danh sách L chứa d} 1 / (k + rank_L(d)),   rank bắt đầu từ 1, k = 60</pre>
 *
 * Bài nằm ở nhiều danh sách (khớp từ khoá <i>và</i> gần nghĩa) tự nhiên được điểm cao hơn.
 */
public final class RankFusion {

    public static final int DEFAULT_K = 60;

    private RankFusion() {
    }

    public static Map<UUID, Double> reciprocalRank(List<List<UUID>> rankedLists, int k) {
        Map<UUID, Double> fused = new HashMap<>();
        for (List<UUID> list : rankedLists) {
            if (list == null) continue;
            for (int i = 0; i < list.size(); i++) {
                fused.merge(list.get(i), 1.0 / (k + i + 1), Double::sum);
            }
        }
        return fused;
    }

    public static Map<UUID, Double> reciprocalRank(List<List<UUID>> rankedLists) {
        return reciprocalRank(rankedLists, DEFAULT_K);
    }
}
