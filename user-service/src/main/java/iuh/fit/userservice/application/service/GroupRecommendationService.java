package iuh.fit.userservice.application.service;

import iuh.fit.userservice.application.service.graph.EgoGraphService;
import iuh.fit.userservice.application.service.graph.PersonalizedPageRank;
import iuh.fit.userservice.domain.entities.Group;
import iuh.fit.userservice.domain.entities.GroupMember;
import iuh.fit.userservice.domain.enums.GroupMemberStatus;
import iuh.fit.userservice.domain.repository.GroupMemberRepository;
import iuh.fit.userservice.domain.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
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
 * Gợi ý nhóm bằng <b>lọc cộng tác trên đồ thị xã hội</b>: một nhóm đáng tham gia nếu những người <i>gần mình trong mạng quan hệ</i>
 * đang ở trong đó.
 *
 * <pre>
 * w(m)      = Personalized PageRank của thành viên m (đã hiệu chỉnh theo bậc, đưa về (0, 1])     // độ gần của m với người xem
 * raw(g)    = Σ_{m ∈ thành viên g} w(m) · (1 + 0,3 nếu m cùng cộng đồng với người xem)
 * score(g)  = raw(g) / ln(2 + |g|)                                                                // IDF: nhóm quá đông kém đặc hiệu
 * </pre>
 *
 * Khác với gợi ý theo embedding mô tả nhóm (chỉ biết nội dung), cách này khai thác hành vi của chính những người bạn quen biết nên
 * bắt được nhóm "ai quanh mình cũng đang tham gia" dù mô tả không liên quan gì tới sở thích đã khai. Loại các nhóm mình đã
 * tham gia, đang chờ duyệt, được mời, bị cấm hoặc bị từ chối.
 */
@Service
@RequiredArgsConstructor
public class GroupRecommendationService {

    static final int NEIGHBOURHOOD_SIZE = 300;
    static final double COMMUNITY_BOOST = 0.3;

    private final EgoGraphService egoGraphService;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupRepository groupRepository;

    public record Recommendation(Group group, double score, List<String> reasons) {
    }

    public List<Recommendation> recommend(UUID viewerId, int limit) {
        EgoGraphService.EgoGraph ego = egoGraphService.build(viewerId);
        if (ego.pprScores().isEmpty()) return List.of();

        // Hàng xóm gần nhất theo PPR (bạn bè bậc 1 thường đứng đầu)
        Map<UUID, Double> unit = PersonalizedPageRank.scaleToUnit(ego.pprScores());
        List<UUID> neighbourhood = unit.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(NEIGHBOURHOOD_SIZE).map(Map.Entry::getKey).toList();
        Set<UUID> direct = ego.adjacency().getOrDefault(viewerId, Set.of());

        Set<UUID> excluded = new HashSet<>();
        for (GroupMember m : groupMemberRepository.findAllByUserId(viewerId)) excluded.add(m.getGroupId());

        Map<UUID, Double> raw = new HashMap<>();
        Map<UUID, Integer> friendsIn = new HashMap<>();
        Map<UUID, Integer> communityIn = new HashMap<>();
        for (GroupMember m : groupMemberRepository.findAllByUserIdInAndStatus(neighbourhood, GroupMemberStatus.APPROVED)) {
            if (excluded.contains(m.getGroupId())) continue;
            boolean community = ego.sameCommunity(m.getUserId());
            double w = unit.getOrDefault(m.getUserId(), 0.0) * (community ? 1.0 + COMMUNITY_BOOST : 1.0);
            raw.merge(m.getGroupId(), w, Double::sum);
            if (direct.contains(m.getUserId())) friendsIn.merge(m.getGroupId(), 1, Integer::sum);
            if (community) communityIn.merge(m.getGroupId(), 1, Integer::sum);
        }
        if (raw.isEmpty()) return List.of();

        Map<UUID, Group> groups = new HashMap<>();
        groupRepository.findAllById(raw.keySet()).forEach(g -> {
            if (g != null && !g.isDeleted()) groups.put(g.getId(), g);
        });

        List<Recommendation> out = new ArrayList<>();
        for (Map.Entry<UUID, Double> e : raw.entrySet()) {
            Group g = groups.get(e.getKey());
            if (g == null) continue;
            double score = e.getValue() / Math.log(2.0 + Math.max(1, g.getMemberCount()));
            List<String> reasons = new ArrayList<>();
            int friends = friendsIn.getOrDefault(g.getId(), 0);
            if (friends > 0) reasons.add(friends + " bạn bè đang ở trong nhóm");
            else reasons.add("Người gần mạng quan hệ của bạn đang tham gia");
            if (communityIn.getOrDefault(g.getId(), 0) > 0) reasons.add("Phổ biến trong cộng đồng bạn bè của bạn");
            out.add(new Recommendation(g, score, reasons));
        }
        out.sort(Comparator.comparingDouble(Recommendation::score).reversed());
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }
}
