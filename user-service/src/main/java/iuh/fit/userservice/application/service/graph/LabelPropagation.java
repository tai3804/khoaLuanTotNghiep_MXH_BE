package iuh.fit.userservice.application.service.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Phát hiện cộng đồng bằng <b>Label Propagation</b> (Raghavan, Albert &amp; Kumara, 2007).
 *
 * <p>Mỗi nút bắt đầu với nhãn riêng. Lần lượt (thứ tự xáo trộn có hạt giống), mỗi nút nhận nhãn xuất hiện nhiều nhất ở các
 * láng giềng; hoà thì chọn nhãn nhỏ nhất để kết quả tất định. Lặp đến khi không còn nút nào đổi nhãn (hoặc hết
 * {@link #MAX_ITERATIONS} vòng). Một nhóm nút kết nối dày đặc nhanh chóng thống nhất một nhãn, còn các cầu nối thưa giữa các
 * nhóm không đủ sức kéo nhãn qua.
 *
 * <p>Chi phí O(iter · |E|), gần tuyến tính, không cần biết trước số cộng đồng — phù hợp tính theo yêu cầu trên đồ thị ego.
 * Kém Louvain ở chỗ không tối ưu độ module (modularity) trực tiếp, đổi lại không cần hợp nhất nhiều tầng và rẻ hơn rõ rệt.
 * Hàm thuần.
 */
public final class LabelPropagation {

    public static final int MAX_ITERATIONS = 20;

    private LabelPropagation() {
    }

    /** @return nhãn cộng đồng (số nguyên) của từng nút; nút cùng nhãn thuộc cùng một cộng đồng */
    public static Map<UUID, Integer> run(Map<UUID, Set<UUID>> adjacency, long seed) {
        List<UUID> nodes = new ArrayList<>(adjacency.keySet());
        for (Set<UUID> adj : adjacency.values()) {
            for (UUID v : adj) if (!adjacency.containsKey(v)) nodes.add(v);
        }
        nodes = new ArrayList<>(new java.util.LinkedHashSet<>(nodes));
        Collections.sort(nodes);

        Map<UUID, Integer> label = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) label.put(nodes.get(i), i);

        Random rnd = new Random(seed);
        List<UUID> order = new ArrayList<>(nodes);
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            Collections.shuffle(order, rnd);
            boolean changed = false;
            for (UUID u : order) {
                Set<UUID> adj = adjacency.get(u);
                if (adj == null || adj.isEmpty()) continue;
                Map<Integer, Integer> count = new HashMap<>();
                for (UUID v : adj) count.merge(label.get(v), 1, Integer::sum);
                int bestLabel = label.get(u);
                int bestCount = count.getOrDefault(bestLabel, 0);
                for (Map.Entry<Integer, Integer> e : count.entrySet()) {
                    int c = e.getValue();
                    if (c > bestCount || (c == bestCount && e.getKey() < bestLabel)) {
                        bestCount = c;
                        bestLabel = e.getKey();
                    }
                }
                if (bestLabel != label.get(u)) {
                    label.put(u, bestLabel);
                    changed = true;
                }
            }
            if (!changed) break;
        }
        return label;
    }
}
