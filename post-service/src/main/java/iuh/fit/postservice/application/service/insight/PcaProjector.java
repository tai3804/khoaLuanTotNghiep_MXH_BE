package iuh.fit.postservice.application.service.insight;

/**
 * Chiếu vector ngữ nghĩa nhiều chiều (768) xuống mặt phẳng 2 chiều bằng phân tích thành phần chính (PCA). Hàm thuần.
 *
 * <pre>
 * 1. Chuẩn hoá mỗi vector về độ dài 1 (độ giống cosine = tích vô hướng), lấy trung bình μ.
 * 2. Hai thành phần chính là hai vector riêng lớn nhất của ma trận hiệp phương sai C = (1/n) Σ (x − μ)(x − μ)ᵀ,
 *    tìm bằng lặp luỹ thừa: v ← C·v / ‖C·v‖ (không cần dựng C kích thước 768×768: C·v = (1/n) Σ (x − μ)·((x − μ)·v)).
 *    Thành phần thứ hai được trực giao hoá với thành phần thứ nhất ở mỗi bước lặp.
 * 3. Toạ độ của một vector u bất kỳ (kể cả vector sở thích người dùng): (x, y) = ((u − μ)·pc1, (u − μ)·pc2).
 * 4. Tỉ lệ phương sai giữ lại của mỗi trục = trị riêng / tổng phương sai.
 * </pre>
 *
 * Dấu của vector riêng là tuỳ ý; để bản đồ không bị lật giữa các lần tính, dấu được cố định sao cho thành phần có trị
 * tuyệt đối lớn nhất là số dương.
 */
public final class PcaProjector {

    private static final int ITERATIONS = 200;
    private static final double TOLERANCE = 1e-9;

    private PcaProjector() {
    }

    /** Cơ sở chiếu: tâm μ, hai trục chính và tỉ lệ phương sai mỗi trục giữ lại. */
    public record Basis(double[] mean, double[] pc1, double[] pc2, double explained1, double explained2) {
        public double[] project(float[] vector) {
            double[] u = unit(vector);
            double x = 0, y = 0;
            for (int i = 0; i < u.length && i < mean.length; i++) {
                double c = u[i] - mean[i];
                x += c * pc1[i];
                y += c * pc2[i];
            }
            return new double[]{x, y};
        }
    }

    public static Basis fit(float[][] vectors) {
        if (vectors == null || vectors.length < 2) throw new IllegalArgumentException("Cần ít nhất 2 vector để chiếu PCA");
        int n = vectors.length;
        int d = vectors[0].length;
        double[][] x = new double[n][];
        double[] mean = new double[d];
        for (int r = 0; r < n; r++) {
            x[r] = unit(vectors[r]);
            for (int i = 0; i < d; i++) mean[i] += x[r][i] / n;
        }
        double total = 0;
        for (double[] row : x) {
            for (int i = 0; i < d; i++) {
                row[i] -= mean[i];
                total += row[i] * row[i];
            }
        }
        total /= n;

        double[] pc1 = powerIteration(x, null, d, 1);
        double[] pc2 = powerIteration(x, pc1, d, 2);
        fixSign(pc1);
        fixSign(pc2);
        double e1 = total > 0 ? rayleigh(x, pc1) / total : 0;
        double e2 = total > 0 ? rayleigh(x, pc2) / total : 0;
        return new Basis(mean, pc1, pc2, e1, e2);
    }

    /** C·v với C là hiệp phương sai của các hàng đã trừ tâm. */
    private static double[] covTimes(double[][] centered, double[] v) {
        double[] out = new double[v.length];
        for (double[] row : centered) {
            double p = dot(row, v);
            for (int i = 0; i < v.length; i++) out[i] += row[i] * p;
        }
        for (int i = 0; i < out.length; i++) out[i] /= centered.length;
        return out;
    }

    private static double[] powerIteration(double[][] centered, double[] orthogonalTo, int d, int seed) {
        // Khởi tạo tất định (không ngẫu nhiên) để cùng dữ liệu luôn cho cùng bản đồ
        double[] v = new double[d];
        for (int i = 0; i < d; i++) v[i] = 1.0 + ((i * 31 + seed * 17) % 7) / 10.0;
        removeComponent(v, orthogonalTo);
        normalizeInPlace(v);
        for (int it = 0; it < ITERATIONS; it++) {
            double[] next = covTimes(centered, v);
            removeComponent(next, orthogonalTo);
            double len = Math.sqrt(dot(next, next));
            if (len < 1e-15) break; // dữ liệu không còn phương sai theo hướng nào khác
            for (int i = 0; i < d; i++) next[i] /= len;
            double diff = 0;
            for (int i = 0; i < d; i++) diff = Math.max(diff, Math.abs(next[i] - v[i]));
            v = next;
            if (diff < TOLERANCE) break;
        }
        return v;
    }

    private static double rayleigh(double[][] centered, double[] v) {
        return dot(covTimes(centered, v), v);
    }

    private static void removeComponent(double[] v, double[] direction) {
        if (direction == null) return;
        double p = dot(v, direction);
        for (int i = 0; i < v.length; i++) v[i] -= p * direction[i];
    }

    private static void fixSign(double[] v) {
        int best = 0;
        for (int i = 1; i < v.length; i++) if (Math.abs(v[i]) > Math.abs(v[best])) best = i;
        if (v[best] < 0) for (int i = 0; i < v.length; i++) v[i] = -v[i];
    }

    private static void normalizeInPlace(double[] v) {
        double len = Math.sqrt(dot(v, v));
        if (len == 0) return;
        for (int i = 0; i < v.length; i++) v[i] /= len;
    }

    static double[] unit(float[] v) {
        double s = 0;
        for (float f : v) s += (double) f * f;
        double len = Math.sqrt(s);
        double[] out = new double[v.length];
        if (len == 0) return out;
        for (int i = 0; i < v.length; i++) out[i] = v[i] / len;
        return out;
    }

    static double dot(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length && i < b.length; i++) s += a[i] * b[i];
        return s;
    }
}
