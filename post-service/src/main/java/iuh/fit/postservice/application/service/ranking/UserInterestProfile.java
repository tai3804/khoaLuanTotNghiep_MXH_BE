package iuh.fit.postservice.application.service.ranking;

import iuh.fit.postservice.application.service.ranking.ItemCollaborativeFilter.Interaction;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Hồ sơ sở thích riêng của một người dùng, dựng từ lịch sử thích, bình luận, chia sẻ (đề xuất theo nội dung,
 * Content-based Filtering). Hàm thuần, không truy cập cơ sở dữ liệu.
 *
 * <h3>Ý tưởng (centroid kiểu Rocchio)</h3>
 * Mỗi bài người dùng từng tương tác có một vector ngữ nghĩa. Vector sở thích là trung bình có trọng số của các vector đó
 * (đã chuẩn hoá độ dài về 1), nên nó nằm "giữa" các chủ đề người dùng quan tâm:
 * <pre>
 * w(i)       = trọng_số_loại · 0,5 ^ (tuổi_ngày / 30)        // thích 1, bình luận 2, chia sẻ 3; nửa đời 30 ngày
 * profile    = chuẩn_hoá( Σ w(i) · v(i)/‖v(i)‖ / Σ w(i) )
 * độ_giống   = cosine(profile, vector bài ứng viên)
 * cộng điểm  = clamp( (độ_giống − 0,45) / 0,55 , 0, 1 )      // 0 nếu độ giống dưới sàn
 * </pre>
 * Chỉ giữ {@link #MAX_ITEMS} bài có trọng số lớn nhất. Người dùng có ít hơn {@link #MIN_ITEMS} bài (khởi đầu lạnh)
 * thì không có hồ sơ.
 */
public final class UserInterestProfile {

    public static final double HALF_LIFE_DAYS = 30.0;
    public static final int MAX_ITEMS = 50;
    public static final int MIN_ITEMS = 3;
    public static final double SIM_FLOOR = 0.45;

    private UserInterestProfile() {
    }

    /** Một bài người dùng quan tâm cùng trọng số đã suy giảm theo thời gian. */
    public record Weighted(UUID postId, double weight) {
    }

    /** Gom tương tác của {@code viewerId} thành danh sách bài có trọng số, lấy tối đa {@link #MAX_ITEMS} bài nặng nhất. */
    public static List<Weighted> aggregate(UUID viewerId, Collection<Interaction> data, LocalDateTime now) {
        if (viewerId == null || data == null) return List.of();
        Map<UUID, Double> byPost = new HashMap<>();
        for (Interaction it : data) {
            if (!viewerId.equals(it.userId()) || it.postId() == null || it.at() == null) continue;
            double ageDays = Math.max(0, Duration.between(it.at(), now).toMinutes() / 1440.0);
            byPost.merge(it.postId(), it.weight() * Math.pow(0.5, ageDays / HALF_LIFE_DAYS), Double::sum);
        }
        List<Weighted> out = new ArrayList<>(byPost.size());
        byPost.forEach((id, w) -> out.add(new Weighted(id, w)));
        out.sort(Comparator.comparingDouble(Weighted::weight).reversed());
        return out.size() > MAX_ITEMS ? new ArrayList<>(out.subList(0, MAX_ITEMS)) : out;
    }

    /**
     * Vector sở thích (độ dài 1) từ các bài có vector. Bài không có vector bị bỏ qua; ít hơn {@link #MIN_ITEMS}
     * bài có vector thì trả về rỗng.
     */
    public static Optional<float[]> centroid(List<Weighted> items, Map<UUID, float[]> embeddings) {
        double[] sum = null;
        double total = 0;
        int used = 0;
        for (Weighted w : items) {
            float[] v = embeddings.get(w.postId());
            if (v == null || v.length == 0 || w.weight() <= 0) continue;
            double norm = norm(v);
            if (norm == 0) continue;
            if (sum == null) sum = new double[v.length];
            if (sum.length != v.length) continue;
            for (int i = 0; i < v.length; i++) sum[i] += w.weight() * v[i] / norm;
            total += w.weight();
            used++;
        }
        if (sum == null || used < MIN_ITEMS || total <= 0) return Optional.empty();
        double len = 0;
        for (int i = 0; i < sum.length; i++) { sum[i] /= total; len += sum[i] * sum[i]; }
        len = Math.sqrt(len);
        if (len == 0) return Optional.empty();
        float[] out = new float[sum.length];
        for (int i = 0; i < sum.length; i++) out[i] = (float) (sum[i] / len);
        return Optional.of(out);
    }

    /** Đổi độ giống cosine thành mức cộng điểm trong [0, 1]; dưới sàn thì 0. */
    public static double boost(double similarity) {
        if (similarity <= SIM_FLOOR) return 0.0;
        return Math.min(1.0, (similarity - SIM_FLOOR) / (1.0 - SIM_FLOOR));
    }

    /** Chuỗi vector theo định dạng của pgvector, ví dụ "[0.1,0.2,0.3]". */
    public static String toVectorString(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 9 + 2).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    /** Các bài đã dùng làm cơ sở, để loại khỏi danh sách cộng điểm (người dùng đã tương tác rồi). */
    public static Set<UUID> postIds(List<Weighted> items) {
        Set<UUID> ids = new java.util.HashSet<>();
        for (Weighted w : items) ids.add(w.postId());
        return ids;
    }

    private static double norm(float[] v) {
        double s = 0;
        for (float x : v) s += (double) x * x;
        return Math.sqrt(s);
    }
}
