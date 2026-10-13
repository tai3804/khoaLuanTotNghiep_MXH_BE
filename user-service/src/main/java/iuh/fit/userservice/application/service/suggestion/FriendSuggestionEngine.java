package iuh.fit.userservice.application.service.suggestion;

import iuh.fit.userservice.domain.entities.GroupMember;
import iuh.fit.userservice.domain.entities.UserConnection;
import iuh.fit.userservice.domain.entities.UserProfile;
import iuh.fit.userservice.domain.enums.ConnectionStatus;
import iuh.fit.userservice.domain.enums.GroupMemberStatus;
import iuh.fit.userservice.domain.repository.GroupMemberRepository;
import iuh.fit.userservice.domain.repository.UserBlockRepository;
import iuh.fit.userservice.domain.repository.UserConnectionRepository;
import iuh.fit.userservice.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tìm và xếp hạng danh sách "Những người bạn có thể biết" cho một người dùng.
 *
 * Nguồn ứng viên (hợp lại):
 *  1. Bạn của bạn (bậc 2), kèm danh sách bạn chung để tính Adamic–Adar.
 *  2. Thành viên các nhóm bạn đang tham gia (nhóm nhỏ được ưu tiên hơn nhóm quá đông).
 *  3. Người đang theo dõi bạn / người bạn đang theo dõi nhưng chưa là bạn bè.
 *  4. Khi còn ít ứng viên (người dùng mới): nhóm khám phá gồm người hoạt động gần đây, có nhiều bạn.
 *
 * Luôn loại: chính mình, bạn bè hiện tại, người bị chặn (hai chiều), người đã có lời mời kết bạn đang chờ
 * hoặc đã bị từ chối (cả hai chiều).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FriendSuggestionEngine {

    private static final int MAX_GROUPS = 25;
    private static final int POOL_TARGET = 60;
    private static final int DISCOVERY_LIMIT = 60;
    private static final int NEWEST_LIMIT = 30;
    private static final int RESULT_CAP = 200;

    private final UserConnectionRepository userConnectionRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserBlockRepository userBlockRepository;
    private final GroupMemberRepository groupMemberRepository;

    public record RankedCandidate(UUID userId, double score, long mutualCount, List<String> reasons) {
    }

    public List<RankedCandidate> rank(UUID viewerId) {
        LocalDateTime now = LocalDateTime.now();

        // ---- 1. Người cần loại trừ
        Set<UUID> friendIds = acceptedFriendIds(viewerId);
        Set<UUID> excluded = new HashSet<>(friendIds);
        excluded.add(viewerId);
        excluded.addAll(userBlockRepository.findAllBlockedUserIdsRelatedTo(viewerId));
        for (UserConnection c : userConnectionRepository.findAllFriendConnectionsInStatuses(
                viewerId, List.of(ConnectionStatus.PENDING, ConnectionStatus.REJECTED, ConnectionStatus.BLOCKED))) {
            excluded.add(other(c, viewerId));
        }

        // ---- 2. Bạn của bạn + bạn chung (Adamic–Adar cần số bạn của từng người bạn chung)
        Map<UUID, List<Integer>> mutualDegrees = new HashMap<>();
        if (!friendIds.isEmpty()) {
            List<UserConnection> fof = userConnectionRepository.findAllAcceptedFriendshipsForUserIds(friendIds);
            // Mọi quan hệ của từng người bạn đều nằm trong `fof` nên số bạn của họ tính được chính xác
            Map<UUID, Integer> degree = new HashMap<>();
            for (UserConnection c : fof) {
                if (friendIds.contains(c.getRequesterId())) degree.merge(c.getRequesterId(), 1, Integer::sum);
                if (friendIds.contains(c.getTargetId())) degree.merge(c.getTargetId(), 1, Integer::sum);
            }
            for (UserConnection c : fof) {
                boolean reqIsFriend = friendIds.contains(c.getRequesterId());
                boolean tgtIsFriend = friendIds.contains(c.getTargetId());
                if (reqIsFriend == tgtIsFriend) continue; // cả hai đều là bạn của mình, hoặc không liên quan
                UUID mutual = reqIsFriend ? c.getRequesterId() : c.getTargetId();
                UUID candidate = reqIsFriend ? c.getTargetId() : c.getRequesterId();
                if (excluded.contains(candidate)) continue;
                mutualDegrees.computeIfAbsent(candidate, k -> new ArrayList<>()).add(degree.getOrDefault(mutual, 1));
            }
        }

        // ---- 3. Nhóm chung
        Map<UUID, Integer> commonGroups = new HashMap<>();
        Map<UUID, Double> commonGroupWeight = new HashMap<>();
        List<GroupMember> mine = groupMemberRepository.findAllByUserIdAndStatus(viewerId, GroupMemberStatus.APPROVED);
        if (!mine.isEmpty()) {
            Set<UUID> groupIds = mine.stream().map(GroupMember::getGroupId).limit(MAX_GROUPS).collect(Collectors.toSet());
            List<GroupMember> members = groupMemberRepository.findAllByGroupIdInAndStatus(groupIds, GroupMemberStatus.APPROVED);
            Map<UUID, Long> groupSize = members.stream()
                    .collect(Collectors.groupingBy(GroupMember::getGroupId, Collectors.counting()));
            for (GroupMember m : members) {
                if (excluded.contains(m.getUserId())) continue;
                commonGroups.merge(m.getUserId(), 1, Integer::sum);
                commonGroupWeight.merge(m.getUserId(),
                        FriendSuggestionScorer.groupWeight(groupSize.getOrDefault(m.getGroupId(), 0L).intValue()), Double::sum);
            }
        }

        // ---- 4. Theo dõi
        Set<UUID> followsMe = new HashSet<>();
        Set<UUID> iFollow = new HashSet<>();
        for (UserConnection c : userConnectionRepository.findAllAcceptedFollowConnections(viewerId)) {
            if (c.getTargetId().equals(viewerId)) followsMe.add(c.getRequesterId());
            else iFollow.add(c.getTargetId());
        }
        followsMe.removeAll(excluded);
        iFollow.removeAll(excluded);

        // ---- 5. Tập ứng viên
        Set<UUID> candidates = new HashSet<>(mutualDegrees.keySet());
        candidates.addAll(commonGroups.keySet());
        candidates.addAll(followsMe);
        candidates.addAll(iFollow);

        if (candidates.size() < POOL_TARGET) {
            Set<UUID> skip = new HashSet<>(excluded);
            skip.addAll(candidates);
            // Người hoạt động gần đây + thành viên mới nhất (thành viên mới có điểm cộng nên không được bỏ sót
            // chỉ vì chưa có lịch sử hoạt động)
            for (UserProfile p : userProfileRepository.findDiscoveryCandidates(skip, PageRequest.of(0, DISCOVERY_LIMIT))) {
                candidates.add(p.getUserId());
                skip.add(p.getUserId());
            }
            for (UserProfile p : userProfileRepository.findNewestMembers(skip, PageRequest.of(0, NEWEST_LIMIT))) {
                candidates.add(p.getUserId());
            }
        }
        candidates.remove(viewerId);
        if (candidates.isEmpty()) return List.of();

        // ---- 6. Hồ sơ và chấm điểm
        Map<UUID, UserProfile> profiles = new HashMap<>();
        List<UUID> toLoad = new ArrayList<>(candidates);
        toLoad.add(viewerId);
        for (UserProfile p : userProfileRepository.findByUserIdIn(toLoad)) profiles.put(p.getUserId(), p);
        UserProfile me = profiles.get(viewerId);

        List<RankedCandidate> ranked = new ArrayList<>();
        for (UUID candidateId : candidates) {
            UserProfile p = profiles.get(candidateId);
            if (p == null) continue; // tài khoản chưa có hồ sơ: không gợi ý

            Integer ageGap = (me != null && me.getDateOfBirth() != null && p.getDateOfBirth() != null)
                    ? Math.abs(me.getDateOfBirth().getYear() - p.getDateOfBirth().getYear()) : null;

            FriendSuggestionScorer.Signals signals = new FriendSuggestionScorer.Signals(
                    mutualDegrees.get(candidateId),
                    commonGroups.getOrDefault(candidateId, 0),
                    commonGroupWeight.getOrDefault(candidateId, 0.0),
                    followsMe.contains(candidateId),
                    iFollow.contains(candidateId),
                    me != null ? me.getLocation() : null,
                    p.getLocation(),
                    ageGap,
                    Boolean.TRUE.equals(p.getIsOnline()),
                    p.getLastActiveAt(),
                    p.getCreatedAt(),
                    p.getFriendCount() == null ? 0 : p.getFriendCount());

            FriendSuggestionScorer.Scored scored = FriendSuggestionScorer.score(viewerId, candidateId, signals, now);
            ranked.add(new RankedCandidate(candidateId, scored.score(), scored.mutualCount(), scored.reasons()));
        }

        ranked.sort(Comparator.comparingDouble(RankedCandidate::score).reversed()
                .thenComparing(RankedCandidate::userId));
        if (ranked.size() > RESULT_CAP) return new ArrayList<>(ranked.subList(0, RESULT_CAP));
        log.debug("Friend suggestions for {}: {} candidates ranked (friends={}, excluded={})",
                viewerId, ranked.size(), friendIds.size(), excluded.size());
        return ranked;
    }

    private Set<UUID> acceptedFriendIds(UUID userId) {
        Set<UUID> ids = new HashSet<>();
        for (UserConnection c : userConnectionRepository.findAllAcceptedFriendships(userId)) {
            ids.add(other(c, userId));
        }
        return ids;
    }

    private static UUID other(UserConnection c, UUID me) {
        return c.getRequesterId().equals(me) ? c.getTargetId() : c.getRequesterId();
    }
}
