package iuh.fit.userservice.application.service.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Personalized PageRank / Random Walk with Restart (Haveliwala 2002; Tong, Faloutsos &amp; Pan 2006) trên đồ thị bạn bè vô hướng.
 *
 * <p>Hình dung một người đi bộ ngẫu nhiên xuất phát từ người dùng {@code s}: ở mỗi bước, với xác suất {@code α} quay về {@code s},
 * với xác suất {@code 1 − α} đi sang một người bạn ngẫu nhiên của nút hiện tại. Phân phối dừng {@code p} thoả
 * <pre>p = α · e_s + (1 − α) · Pᵀ · p,     P[u][v] = 1/deg(u) nếu u – v là bạn</pre>
 * và được giải bằng <b>lặp luỹ thừa</b> (power iteration) cho đến khi tổng chênh lệch L1 nhỏ hơn {@code tol}. Chi phí
 * O(iter · |E|) trên đồ thị ego nhỏ. Nút nhận được khối lượng xác suất lớn nếu có <i>nhiều đường đi ngắn</i> từ {@code s}.
 *
 * <h3>Hơn "số bạn chung" (Adamic–Adar) ở đâu</h3>
 * <ul>
 *   <li>Đi được qua nhiều bước (bậc 3, 4) nên thấy được quan hệ gián tiếp mà bạn chung bằng 0;</li>
 *   <li>Cộng dồn <i>mọi</i> đường đi, không chỉ các đường dài 2 bước, và đường ngắn nặng hơn đường dài theo cấp số nhân {@code (1 − α)^độ_dài};</li>
 *   <li>Nút không có đường đi nào từ {@code s} (khác thành phần liên thông) tự nhiên có điểm 0.</li>
 * </ul>
 *
 * <h3>Hiệu chỉnh nút trung tâm</h3>
 * Phân phối dừng của bước đi ngẫu nhiên tỉ lệ thuận với bậc nên "người quen tất cả mọi người" luôn nổi lên. {@link #degreeNormalized}
 * chia điểm cho {@code deg^β} (β mặc định 0,5), cùng tinh thần với trọng số {@code 1/ln(bậc)} của Adamic–Adar.
 *
 * <p>Nút "dangling" (không còn cạnh) đẩy khối lượng của nó về lại {@code s}. Hàm thuần, tất định.
 */
public final class PersonalizedPageRank {

    public static final double DEFAULT_RESTART = 0.15;
    public static final int DEFAULT_MAX_ITERATIONS = 50;
    public static final double DEFAULT_TOLERANCE = 1e-7;
    public static final double DEFAULT_DEGREE_EXPONENT = 0.5;

    private PersonalizedPageRank() {
    }

    /**
     * @param adjacency đồ thị vô hướng: mỗi cạnh u–v phải xuất hiện ở cả {@code adjacency[u]} lẫn {@code adjacency[v]}
     * @param source    người dùng xuất phát
     * @param restart   xác suất quay về {@code source} ở mỗi bước (0 &lt; α &lt; 1)
     * @return điểm của mọi nút khác {@code source} có điểm dương; rỗng nếu {@code source} không có bạn
     */
    public static Map<UUID, Double> compute(Map<UUID, Set<UUID>> adjacency, UUID source, double restart,
                                            int maxIterations, double tolerance) {
        Set<UUID> sourceNeighbours = adjacency.get(source);
        if (sourceNeighbours == null || sourceNeighbours.isEmpty()) return Map.of();
        double alpha = Math.min(0.99, Math.max(0.01, restart));

        // Đánh chỉ số các nút để dùng mảng thay cho bản đồ trong vòng lặp nóng
        Map<UUID, Integer> index = new HashMap<>();
        List<UUID> nodes = new ArrayList<>();
        for (Map.Entry<UUID, Set<UUID>> e : adjacency.entrySet()) {
            if (index.putIfAbsent(e.getKey(), nodes.size()) == null) nodes.add(e.getKey());
            for (UUID v : e.getValue()) {
                if (index.putIfAbsent(v, nodes.size()) == null) nodes.add(v);
            }
        }
        int n = nodes.size();
        int[][] neighbours = new int[n][];
        for (int i = 0; i < n; i++) {
            Set<UUID> adj = adjacency.get(nodes.get(i));
            if (adj == null) {
                neighbours[i] = new int[0];
            } else {
                int[] arr = new int[adj.size()];
                int k = 0;
                for (UUID v : adj) arr[k++] = index.get(v);
                neighbours[i] = arr;
            }
        }

        int s = index.get(source);
        double[] p = new double[n];
        p[s] = 1.0;
        double[] next = new double[n];

        for (int iter = 0; iter < maxIterations; iter++) {
            java.util.Arrays.fill(next, 0.0);
            double dangling = 0;
            for (int u = 0; u < n; u++) {
                if (p[u] == 0) continue;
                int[] adj = neighbours[u];
                if (adj.length == 0) {
                    dangling += p[u];
                    continue;
                }
                double share = (1.0 - alpha) * p[u] / adj.length;
                for (int v : adj) next[v] += share;
            }
            next[s] += alpha + (1.0 - alpha) * dangling;

            double diff = 0;
            for (int i = 0; i < n; i++) diff += Math.abs(next[i] - p[i]);
            double[] tmp = p;
            p = next;
            next = tmp;
            if (diff < tolerance) break;
        }

        Map<UUID, Double> out = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (i != s && p[i] > 1e-12) out.put(nodes.get(i), p[i]);
        }
        return out;
    }

    public static Map<UUID, Double> compute(Map<UUID, Set<UUID>> adjacency, UUID source) {
        return compute(adjacency, source, DEFAULT_RESTART, DEFAULT_MAX_ITERATIONS, DEFAULT_TOLERANCE);
    }

    /** Chia điểm cho {@code deg^β} để người có quá nhiều bạn không luôn đứng đầu. */
    public static Map<UUID, Double> degreeNormalized(Map<UUID, Double> scores, Map<UUID, Set<UUID>> adjacency, double beta) {
        Map<UUID, Double> out = new HashMap<>(scores.size());
        for (Map.Entry<UUID, Double> e : scores.entrySet()) {
            Set<UUID> adj = adjacency.get(e.getKey());
            int degree = adj == null ? 1 : Math.max(1, adj.size());
            out.put(e.getKey(), e.getValue() / Math.pow(degree, beta));
        }
        return out;
    }

    /** Đưa điểm về (0, 1] bằng cách chia cho điểm lớn nhất. */
    public static Map<UUID, Double> scaleToUnit(Map<UUID, Double> scores) {
        double max = 0;
        for (double v : scores.values()) max = Math.max(max, v);
        if (max <= 0) return Map.of();
        Map<UUID, Double> out = new HashMap<>(scores.size());
        for (Map.Entry<UUID, Double> e : scores.entrySet()) out.put(e.getKey(), e.getValue() / max);
        return out;
    }
}
