package iuh.fit.postservice.application.service.ranking;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lọc cộng tác dựa trên bài viết (Item-based Collaborative Filtering) cho phản hồi ngầm (implicit feedback).
 *
 * <h3>Dữ liệu</h3>
 * Ma trận thưa người dùng × bài viết. Mỗi tương tác có trọng số theo loại (thích 1, bình luận 2, chia sẻ 3, cùng
 * thang với công thức mức tương tác của bảng tin) và suy giảm theo thời gian với chu kỳ bán rã 14 ngày:
 * <pre>w(u, i) = Σ trọng_số_loại · 0,5 ^ (tuổi_ngày / 14)</pre>
 *
 * <h3>Độ giống giữa hai bài</h3>
 * Cosine giữa hai cột của ma trận, tính trên những người dùng khác người đang xem, kèm hệ số co (shrinkage)
 * để một cặp chỉ có một người chung không bị coi là giống tuyệt đối:
 * <pre>sim(a, b) = cos(a, b) · n / (n + λ),  n = số người dùng cùng tương tác với cả a và b,  λ = 2</pre>
 *
 * <h3>Dự đoán</h3>
 * Điểm của bài ứng viên a cho người xem v là tổng độ giống với các bài v đã tương tác, mỗi bài được nhân với
 * trọng số đã chuẩn hoá theo bài v quan tâm nhất (ŵ = w / w_max), rồi chặn trên ở 1:
 * <pre>điểm(v, a) = min(1, Σ_b sim(a, b) · ŵ(v, b))   ∈ [0, 1]</pre>
 *
 * Người xem có ít hơn {@link #MIN_VIEWER_ITEMS} bài đã tương tác (khởi đầu lạnh) thì không có điểm cộng tác;
 * bảng tin chỉ còn dùng tín hiệu theo nội dung và quan hệ xã hội (lai kiểu chuyển đổi).
 */
public final class ItemCollaborativeFilter {

    public static final double W_REACTION = 1.0;
    public static final double W_COMMENT = 2.0;
    public static final double W_SHARE = 3.0;
    public static final double HALF_LIFE_DAYS = 14.0;
    public static final double SHRINKAGE = 2.0;
    public static final int MIN_VIEWER_ITEMS = 2;

    private ItemCollaborativeFilter() {
    }

    /** Một lần tương tác của người dùng với bài viết. {@code weight} là trọng số theo loại tương tác. */
    public record Interaction(UUID userId, UUID postId, double weight, LocalDateTime at) {
    }

    /**
     * @param viewerId   người xem
     * @param candidates bài ứng viên cần chấm điểm
     * @param data       toàn bộ tương tác gần đây của mọi người dùng (kể cả người xem)
     * @param now        thời điểm tính suy giảm
     * @return điểm trong (0, 1] cho từng ứng viên có ít nhất một bài giống; bài không có điểm thì không xuất hiện
     */
    public static Map<UUID, Double> score(UUID viewerId, Collection<UUID> candidates,
                                          Collection<Interaction> data, LocalDateTime now) {
        if (viewerId == null || candidates.isEmpty() || data.isEmpty()) return Map.of();

        // 1. Gom trọng số theo (người dùng, bài) có suy giảm thời gian
        Map<UUID, Map<UUID, Double>> byUser = new HashMap<>();
        for (Interaction it : data) {
            if (it.userId() == null || it.postId() == null) continue;
            double ageDays = Math.max(0, Duration.between(it.at(), now).toMinutes() / 1440.0);
            double w = it.weight() * Math.pow(0.5, ageDays / HALF_LIFE_DAYS);
            byUser.computeIfAbsent(it.userId(), k -> new HashMap<>()).merge(it.postId(), w, Double::sum);
        }

        // 2. Bài người xem đã tương tác
        Map<UUID, Double> viewerItems = byUser.getOrDefault(viewerId, Map.of());
        if (viewerItems.size() < MIN_VIEWER_ITEMS) return Map.of();
        double viewerMax = viewerItems.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        if (viewerMax <= 0) return Map.of();

        Set<UUID> candidateSet = new HashSet<>(candidates);

        // 3. Tích vô hướng, độ dài cột và số người chung, chỉ trên những người dùng khác người xem
        Map<UUID, Double> norm2 = new HashMap<>();
        Map<UUID, Map<UUID, double[]>> dot = new HashMap<>(); // a (ứng viên) -> b (bài đã xem) -> {tích, số người chung}
        for (Map.Entry<UUID, Map<UUID, Double>> e : byUser.entrySet()) {
            if (e.getKey().equals(viewerId)) continue;
            Map<UUID, Double> items = e.getValue();
            List<UUID> aList = new ArrayList<>();
            List<UUID> bList = new ArrayList<>();
            for (Map.Entry<UUID, Double> ie : items.entrySet()) {
                UUID post = ie.getKey();
                if (candidateSet.contains(post)) aList.add(post);
                if (viewerItems.containsKey(post)) bList.add(post);
                if (candidateSet.contains(post) || viewerItems.containsKey(post)) {
                    norm2.merge(post, ie.getValue() * ie.getValue(), Double::sum);
                }
            }
            for (UUID a : aList) {
                for (UUID b : bList) {
                    if (a.equals(b)) continue;
                    double[] cell = dot.computeIfAbsent(a, k -> new HashMap<>()).computeIfAbsent(b, k -> new double[2]);
                    cell[0] += items.get(a) * items.get(b);
                    cell[1] += 1;
                }
            }
        }

        // 4. Dự đoán
        Map<UUID, Double> result = new HashMap<>();
        for (Map.Entry<UUID, Map<UUID, double[]>> ae : dot.entrySet()) {
            UUID a = ae.getKey();
            double na = Math.sqrt(norm2.getOrDefault(a, 0.0));
            if (na == 0) continue;
            double sum = 0;
            for (Map.Entry<UUID, double[]> be : ae.getValue().entrySet()) {
                double nb = Math.sqrt(norm2.getOrDefault(be.getKey(), 0.0));
                if (nb == 0) continue;
                double cos = be.getValue()[0] / (na * nb);
                double n = be.getValue()[1];
                double sim = cos * n / (n + SHRINKAGE);
                sum += sim * viewerItems.get(be.getKey()) / viewerMax;
            }
            double predicted = Math.min(1.0, sum);
            if (predicted > 0) result.put(a, predicted);
        }
        return result;
    }
}
