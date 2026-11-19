package iuh.fit.userservice.application.service.graph;

import iuh.fit.userservice.domain.entities.UserConnection;
import iuh.fit.userservice.domain.repository.UserConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dựng <b>đồ thị ego</b> bán kính 3 quanh một người dùng và chạy các thuật toán đồ thị trên đó:
 * Personalized PageRank ({@link PersonalizedPageRank}) và phát hiện cộng đồng ({@link LabelPropagation}).
 *
 * <p>Không thể tải toàn bộ đồ thị xã hội, nên mở rộng theo từng lớp:
 * <ol>
 *   <li>bậc 1: bạn của người xem; bậc 2: các cạnh của bạn (đã có sẵn khi gợi ý kết bạn);</li>
 *   <li>bậc 3: các cạnh của tối đa {@value #HOP3_SEEDS} người ở bậc 2 có nhiều bạn chung nhất (chặn trên {@value #MAX_EDGES} cạnh).</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EgoGraphService {

    static final int HOP3_SEEDS = 100;
    static final int MAX_EDGES = 20_000;
    static final long COMMUNITY_SEED = 42L;

    private final UserConnectionRepository userConnectionRepository;

    /**
     * @param adjacency       đồ thị vô hướng của vùng quanh người xem
     * @param pprScores       điểm Personalized PageRank đã hiệu chỉnh theo bậc (chia cho deg^0,5) của mọi nút khác người xem
     * @param communities     nhãn cộng đồng của từng nút
     */
    public record EgoGraph(UUID viewer, Map<UUID, Set<UUID>> adjacency, Map<UUID, Double> pprScores,
                           Map<UUID, Integer> communities) {

        public boolean sameCommunity(UUID other) {
            Integer a = communities.get(viewer);
            Integer b = communities.get(other);
            return a != null && a.equals(b);
        }

        public static EgoGraph empty(UUID viewer) {
            return new EgoGraph(viewer, Map.of(), Map.of(), Map.of());
        }
    }

    /** Dùng khi đã có sẵn các cạnh liên quan tới bạn của người xem (tránh truy vấn lặp). */
    public EgoGraph build(UUID viewerId, Set<UUID> friendIds, List<UserConnection> friendEdges) {
        if (friendIds.isEmpty()) return EgoGraph.empty(viewerId);

        Map<UUID, Set<UUID>> adj = new HashMap<>();
        int edges = 0;
        for (UserConnection c : friendEdges) {
            addEdge(adj, c.getRequesterId(), c.getTargetId());
            edges++;
        }
        // Cạnh giữa người xem và bạn bè (có thể chưa nằm trong friendEdges nếu người gọi chỉ truyền cạnh bậc 2)
        for (UUID f : friendIds) addEdge(adj, viewerId, f);

        // Bậc 3: mở rộng từ các nút bậc 2 có nhiều bạn chung nhất
        Map<UUID, Integer> viaFriends = new HashMap<>();
        for (UUID f : friendIds) {
            for (UUID v : adj.getOrDefault(f, Set.of())) {
                if (!v.equals(viewerId) && !friendIds.contains(v)) viaFriends.merge(v, 1, Integer::sum);
            }
        }
        List<UUID> seeds = viaFriends.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey(Comparator.naturalOrder())))
                .limit(HOP3_SEEDS).map(Map.Entry::getKey).toList();
        if (!seeds.isEmpty() && edges < MAX_EDGES) {
            for (UserConnection c : userConnectionRepository.findAllAcceptedFriendshipsForUserIds(seeds)) {
                if (++edges > MAX_EDGES) break;
                addEdge(adj, c.getRequesterId(), c.getTargetId());
            }
        }

        Map<UUID, Double> ppr = PersonalizedPageRank.compute(adj, viewerId);
        Map<UUID, Double> normalised = PersonalizedPageRank.degreeNormalized(ppr, adj, PersonalizedPageRank.DEFAULT_DEGREE_EXPONENT);
        Map<UUID, Integer> communities = LabelPropagation.run(adj, COMMUNITY_SEED);
        log.debug("Ego graph for {}: {} nodes, {} edges loaded, {} scored", viewerId, adj.size(), edges, normalised.size());
        return new EgoGraph(viewerId, adj, normalised, communities);
    }

    /** Tự tải bạn bè và cạnh bậc 2 (dùng cho các tính năng không có sẵn dữ liệu này, ví dụ gợi ý nhóm). */
    public EgoGraph build(UUID viewerId) {
        Set<UUID> friendIds = new HashSet<>();
        for (UserConnection c : userConnectionRepository.findAllAcceptedFriendships(viewerId)) {
            friendIds.add(c.getRequesterId().equals(viewerId) ? c.getTargetId() : c.getRequesterId());
        }
        if (friendIds.isEmpty()) return EgoGraph.empty(viewerId);
        return build(viewerId, friendIds, new ArrayList<>(userConnectionRepository.findAllAcceptedFriendshipsForUserIds(friendIds)));
    }

    private static void addEdge(Map<UUID, Set<UUID>> adj, UUID a, UUID b) {
        if (a == null || b == null || a.equals(b)) return;
        adj.computeIfAbsent(a, k -> new HashSet<>()).add(b);
        adj.computeIfAbsent(b, k -> new HashSet<>()).add(a);
    }
}
