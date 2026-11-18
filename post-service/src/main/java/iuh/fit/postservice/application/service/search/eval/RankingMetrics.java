package iuh.fit.postservice.application.service.search.eval;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Các thước đo chất lượng xếp hạng chuẩn của truy hồi thông tin. Hàm thuần.
 *
 * <p>Nhãn là mức liên quan nguyên {@code grade ≥ 0} (0 = không liên quan, 1 = hơi liên quan, 2 = liên quan, 3 = rất liên quan).
 * Một tài liệu "liên quan" khi {@code grade > 0}. Tài liệu không có trong bảng nhãn coi như {@code grade = 0}.
 *
 * <ul>
 *   <li><b>Precision@k</b> = (số tài liệu liên quan trong top-k) / k</li>
 *   <li><b>Recall@k</b> = (số tài liệu liên quan trong top-k) / (tổng số tài liệu liên quan đã gán nhãn)</li>
 *   <li><b>AP</b> (Average Precision) = trung bình của Precision@i tại mỗi vị trí i có tài liệu liên quan, chia cho tổng số
 *       tài liệu liên quan; MAP là trung bình AP trên các truy vấn</li>
 *   <li><b>RR</b> (Reciprocal Rank) = 1 / vị trí của tài liệu liên quan đầu tiên; MRR là trung bình trên các truy vấn</li>
 *   <li><b>NDCG@k</b> = DCG@k / IDCG@k, với {@code DCG@k = Σ (2^grade − 1) / log2(i + 1)} (Järvelin &amp; Kekäläinen, 2002)</li>
 * </ul>
 */
public final class RankingMetrics {

    private RankingMetrics() {
    }

    public static double precisionAtK(List<UUID> ranked, Map<UUID, Integer> grades, int k) {
        if (k <= 0) return 0;
        int hits = 0;
        for (int i = 0; i < Math.min(k, ranked.size()); i++) {
            if (grade(grades, ranked.get(i)) > 0) hits++;
        }
        return (double) hits / k;
    }

    public static double recallAtK(List<UUID> ranked, Map<UUID, Integer> grades, int k) {
        int relevant = relevantCount(grades);
        if (relevant == 0) return 0;
        int hits = 0;
        for (int i = 0; i < Math.min(k, ranked.size()); i++) {
            if (grade(grades, ranked.get(i)) > 0) hits++;
        }
        return (double) hits / relevant;
    }

    public static double averagePrecision(List<UUID> ranked, Map<UUID, Integer> grades) {
        int relevant = relevantCount(grades);
        if (relevant == 0) return 0;
        int hits = 0;
        double sum = 0;
        for (int i = 0; i < ranked.size(); i++) {
            if (grade(grades, ranked.get(i)) > 0) {
                hits++;
                sum += (double) hits / (i + 1);
            }
        }
        return sum / relevant;
    }

    public static double reciprocalRank(List<UUID> ranked, Map<UUID, Integer> grades) {
        for (int i = 0; i < ranked.size(); i++) {
            if (grade(grades, ranked.get(i)) > 0) return 1.0 / (i + 1);
        }
        return 0;
    }

    public static double ndcgAtK(List<UUID> ranked, Map<UUID, Integer> grades, int k) {
        double dcg = 0;
        for (int i = 0; i < Math.min(k, ranked.size()); i++) {
            dcg += gain(grade(grades, ranked.get(i))) / log2(i + 2);
        }
        List<Integer> ideal = grades.values().stream().filter(g -> g > 0).sorted((a, b) -> b - a).toList();
        double idcg = 0;
        for (int i = 0; i < Math.min(k, ideal.size()); i++) {
            idcg += gain(ideal.get(i)) / log2(i + 2);
        }
        return idcg == 0 ? 0 : dcg / idcg;
    }

    private static int grade(Map<UUID, Integer> grades, UUID id) {
        Integer g = grades.get(id);
        return g == null ? 0 : Math.max(0, g);
    }

    private static int relevantCount(Map<UUID, Integer> grades) {
        int c = 0;
        for (int g : grades.values()) if (g > 0) c++;
        return c;
    }

    private static double gain(int grade) {
        return Math.pow(2, grade) - 1;
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }
}
