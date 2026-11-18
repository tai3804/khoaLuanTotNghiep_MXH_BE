package iuh.fit.postservice.application.service.topic;

import java.util.Random;

/**
 * Gom cụm chủ đề từ vector ngữ nghĩa bằng <b>k-means cầu</b> (spherical k-means, độ đo cosine) khởi tạo bằng
 * <b>k-means++</b> (Arthur &amp; Vassilvitskii, 2007), và chọn số cụm k bằng <b>hệ số silhouette</b> (Rousseeuw, 1987).
 *
 * <h3>Vì sao dạng "cầu"</h3>
 * Embedding so sánh bằng cosine nên mọi vector được chuẩn hoá về độ dài 1 và tâm cụm là <i>trung bình đã chuẩn hoá</i>
 * (vector trung bình chiếu lại lên mặt cầu). Gán cụm theo tích vô hướng lớn nhất tương đương khoảng cách cosine nhỏ nhất.
 *
 * <h3>k-means++</h3>
 * Tâm đầu chọn ngẫu nhiên; mỗi tâm kế tiếp chọn với xác suất tỉ lệ với {@code D(x)²}, trong đó D là khoảng cách cosine
 * {@code 1 − cos} tới tâm gần nhất đã chọn. Cách này rải các tâm ban đầu ra xa nhau và có bảo đảm xấp xỉ O(log k) so với tối ưu.
 *
 * <h3>Chọn k</h3>
 * Với mỗi k thử, silhouette của điểm i là {@code s(i) = (b(i) − a(i)) / max(a(i), b(i))}: a là khoảng cách trung bình tới cùng cụm,
 * b là khoảng cách trung bình tới cụm gần nhất khác. k có silhouette trung bình lớn nhất được chọn. Ma trận cosine giữa mọi cặp
 * chỉ tính một lần (O(n²d)) và dùng chung cho mọi k.
 *
 * Tất định theo {@code seed}. Hàm thuần, không truy cập cơ sở dữ liệu.
 */
public final class TopicClusterer {

    static final int MAX_ITERATIONS = 40;

    private TopicClusterer() {
    }

    /** {@code assignment[i]} là cụm của điểm i; {@code centroids[c]} là tâm (độ dài 1) của cụm c. */
    public record Clustering(int k, int[] assignment, float[][] centroids, double silhouette) {
    }

    /** Thử k từ {@code kMin} đến {@code kMax} và trả về phân cụm có silhouette lớn nhất. */
    public static Clustering best(float[][] vectors, int kMin, int kMax, long seed) {
        int n = vectors.length;
        if (n == 0) return new Clustering(0, new int[0], new float[0][], 0);
        float[][] unit = new float[n][];
        for (int i = 0; i < n; i++) unit[i] = normalize(vectors[i]);

        int hi = Math.min(kMax, n);
        int lo = Math.max(2, Math.min(kMin, hi));
        if (n < 3 || hi < 2) {
            return new Clustering(1, new int[n], new float[][]{mean(unit, new int[n], 0, 1)}, 0);
        }

        float[][] sim = similarityMatrix(unit);
        Clustering best = null;
        for (int k = lo; k <= hi; k++) {
            Clustering c = kMeans(unit, k, seed);
            double s = silhouette(sim, c.assignment(), k);
            if (best == null || s > best.silhouette() + 1e-12) {
                best = new Clustering(k, c.assignment(), c.centroids(), s);
            }
        }
        return best;
    }

    /** k-means cầu với khởi tạo k-means++; {@code unit} phải là vector độ dài 1. */
    public static Clustering kMeans(float[][] unit, int k, long seed) {
        int n = unit.length;
        Random rnd = new Random(seed + 31L * k);
        float[][] centroids = seedPlusPlus(unit, k, rnd);
        int[] assignment = new int[n];
        java.util.Arrays.fill(assignment, -1);

        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            boolean changed = false;
            for (int i = 0; i < n; i++) {
                int bestC = 0;
                double bestSim = -2;
                for (int c = 0; c < k; c++) {
                    double s = dot(unit[i], centroids[c]);
                    if (s > bestSim) {
                        bestSim = s;
                        bestC = c;
                    }
                }
                if (assignment[i] != bestC) {
                    assignment[i] = bestC;
                    changed = true;
                }
            }
            if (!changed) break;

            for (int c = 0; c < k; c++) {
                float[] m = mean(unit, assignment, c, 1);
                if (m == null) {
                    centroids[c] = unit[farthestPoint(unit, assignment, centroids)].clone();   // cụm rỗng: gieo lại ở điểm xa nhất
                } else {
                    centroids[c] = m;
                }
            }
        }
        return new Clustering(k, assignment, centroids, 0);
    }

    private static float[][] seedPlusPlus(float[][] unit, int k, Random rnd) {
        int n = unit.length;
        float[][] centroids = new float[k][];
        centroids[0] = unit[rnd.nextInt(n)].clone();
        double[] d2 = new double[n];
        for (int c = 1; c < k; c++) {
            double total = 0;
            for (int i = 0; i < n; i++) {
                double best = 2;
                for (int j = 0; j < c; j++) best = Math.min(best, 1.0 - dot(unit[i], centroids[j]));
                d2[i] = best * best;
                total += d2[i];
            }
            int pick;
            if (total <= 1e-12) {
                pick = rnd.nextInt(n);
            } else {
                double r = rnd.nextDouble() * total;
                pick = n - 1;
                double acc = 0;
                for (int i = 0; i < n; i++) {
                    acc += d2[i];
                    if (acc >= r) {
                        pick = i;
                        break;
                    }
                }
            }
            centroids[c] = unit[pick].clone();
        }
        return centroids;
    }

    private static int farthestPoint(float[][] unit, int[] assignment, float[][] centroids) {
        int worst = 0;
        double worstSim = 2;
        for (int i = 0; i < unit.length; i++) {
            double s = assignment[i] >= 0 && centroids[assignment[i]] != null ? dot(unit[i], centroids[assignment[i]]) : -1;
            if (s < worstSim) {
                worstSim = s;
                worst = i;
            }
        }
        return worst;
    }

    /** Hệ số silhouette trung bình theo khoảng cách cosine {@code 1 − cos}. Cụm chỉ có một điểm có s = 0. */
    public static double silhouette(float[][] sim, int[] assignment, int k) {
        int n = assignment.length;
        int[] size = new int[k];
        for (int a : assignment) size[a]++;
        double total = 0;
        for (int i = 0; i < n; i++) {
            double[] sum = new double[k];
            for (int j = 0; j < n; j++) {
                if (j != i) sum[assignment[j]] += 1.0 - sim[i][j];
            }
            int own = assignment[i];
            if (size[own] <= 1) continue;
            double a = sum[own] / (size[own] - 1);
            double b = Double.MAX_VALUE;
            for (int c = 0; c < k; c++) {
                if (c != own && size[c] > 0) b = Math.min(b, sum[c] / size[c]);
            }
            if (b == Double.MAX_VALUE) continue;
            double denom = Math.max(a, b);
            if (denom > 1e-12) total += (b - a) / denom;
        }
        return total / n;
    }

    static float[][] similarityMatrix(float[][] unit) {
        int n = unit.length;
        float[][] sim = new float[n][n];
        for (int i = 0; i < n; i++) {
            sim[i][i] = 1f;
            for (int j = i + 1; j < n; j++) {
                float s = (float) dot(unit[i], unit[j]);
                sim[i][j] = s;
                sim[j][i] = s;
            }
        }
        return sim;
    }

    /** Trung bình đã chuẩn hoá của các điểm thuộc cụm {@code cluster}; null nếu cụm rỗng. */
    private static float[] mean(float[][] unit, int[] assignment, int cluster, int unused) {
        double[] sum = null;
        int count = 0;
        for (int i = 0; i < unit.length; i++) {
            if (assignment[i] != cluster) continue;
            if (sum == null) sum = new double[unit[i].length];
            for (int d = 0; d < sum.length; d++) sum[d] += unit[i][d];
            count++;
        }
        if (count == 0) return null;
        float[] m = new float[sum.length];
        for (int d = 0; d < sum.length; d++) m[d] = (float) (sum[d] / count);
        return normalize(m);
    }

    static float[] normalize(float[] v) {
        double s = 0;
        for (float x : v) s += (double) x * x;
        double norm = Math.sqrt(s);
        if (norm == 0) return v.clone();
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / norm);
        return out;
    }

    static double dot(float[] a, float[] b) {
        int len = Math.min(a.length, b.length);
        double s = 0;
        for (int i = 0; i < len; i++) s += (double) a[i] * b[i];
        return s;
    }
}
