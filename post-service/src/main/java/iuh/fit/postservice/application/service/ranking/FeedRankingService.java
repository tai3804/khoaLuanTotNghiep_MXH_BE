package iuh.fit.postservice.application.service.ranking;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.postservice.application.service.PostSeenService;
import iuh.fit.postservice.application.service.ReputationGuard;
import iuh.fit.postservice.application.service.PostVisibilityService;
import iuh.fit.postservice.application.service.ranking.PostRelevanceScorer.Item;
import iuh.fit.postservice.application.service.ranking.PostRelevanceScorer.Mode;
import iuh.fit.postservice.application.service.ranking.PostRelevanceScorer.Scored;
import iuh.fit.postservice.application.service.ranking.PostRelevanceScorer.Signals;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.infrastructure.client.user.UserConnectionClient;
import iuh.fit.postservice.infrastructure.persistence.repository.CommentRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostMediaRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.ReactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Xếp hạng bảng tin cho một người xem: lấy tập bài ứng viên gần đây, lọc theo quyền xem, chấm điểm bằng
 * {@link PostRelevanceScorer}, đa dạng hoá theo tác giả rồi lưu bản chụp để phân trang ổn định.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedRankingService {

    private static final int CANDIDATE_LIMIT = 400;
    private static final int MIN_CANDIDATES = 40;
    private static final int RESULT_CAP = 200;
    private static final int INTERACTION_WINDOW_DAYS = 30;

    private final PostRepository postRepository;
    private final PostMediaRepository postMediaRepository;
    private final ReactionRepository reactionRepository;
    private final CommentRepository commentRepository;
    private final PostVisibilityService postVisibilityService;
    private final UserConnectionClient userConnectionClient;
    private final RankedFeedCache rankedFeedCache;
    private final PostSeenService postSeenService;
    private final ReputationGuard reputationGuard;

    public RankedFeedSnapshot snapshot(UUID viewerId, Mode mode) {
        return snapshot(viewerId, mode, false, false);
    }

    /**
     * @param unseenOnly   bỏ các bài người xem đã xem (trừ bài của chính họ); lưu bản chụp riêng với bản đầy đủ
     * @param forceRefresh bỏ qua bản chụp đang có và tính lại (dùng cho lần tải trang 1 để bài vừa xem biến mất);
     *                     các trang sau của cùng phiên cuộn vẫn cắt từ bản chụp mới này nên không bị lặp hay nhảy cóc
     */
    public RankedFeedSnapshot snapshot(UUID viewerId, Mode mode, boolean unseenOnly, boolean forceRefresh) {
        if (!forceRefresh) {
            Optional<RankedFeedSnapshot> cached = rankedFeedCache.get(viewerId, mode, unseenOnly);
            if (cached.isPresent()) return cached.get();
        }
        RankedFeedSnapshot computed = compute(viewerId, mode, unseenOnly);
        rankedFeedCache.put(viewerId, mode, unseenOnly, computed);
        return computed;
    }

    RankedFeedSnapshot compute(UUID viewerId, Mode mode, boolean unseenOnly) {
        LocalDateTime now = LocalDateTime.now();

        // 1. Ứng viên: bắt đầu với cửa sổ thời gian hẹp, nới dần nếu có quá ít bài (hệ thống mới / ít người dùng)
        int[] windowsDays = mode == Mode.TRENDING ? new int[]{3, 14, 60} : new int[]{14, 60, 365};
        List<Post> candidates = List.of();
        for (int days : windowsDays) {
            candidates = postRepository.findRankingCandidates(now.minusDays(days), PageRequest.of(0, CANDIDATE_LIMIT));
            if (candidates.size() >= MIN_CANDIDATES) break;
        }

        // 2. Quyền xem
        Set<UUID> visibleIds = postVisibilityService.visiblePostIds(candidates, viewerId, true);
        List<Post> visible = candidates.stream().filter(p -> visibleIds.contains(p.getId())).toList();

        // 2b. Bài đã xem rồi thì không đưa lại (bài của chính mình luôn được giữ)
        if (unseenOnly && !visible.isEmpty()) {
            Set<UUID> seen = postSeenService.seenAmong(viewerId, visible.stream().map(Post::getId).toList());
            if (!seen.isEmpty()) {
                visible = visible.stream()
                        .filter(p -> !seen.contains(p.getId()) || viewerId.equals(p.getAuthorId()))
                        .toList();
            }
        }
        if (visible.isEmpty()) return new RankedFeedSnapshot();

        // 3. Dữ liệu chấm điểm
        Set<UUID> friendIds = new HashSet<>();
        Set<UUID> followingIds = new HashSet<>();
        loadSocialGraph(friendIds, followingIds);
        Set<UUID> memberGroups = mode == Mode.RELEVANCE ? postVisibilityService.memberGroupIds(visible) : Set.of();
        Map<UUID, Integer> interactions = mode == Mode.RELEVANCE ? loadInteractions(viewerId, now) : Map.of();
        Set<UUID> postsWithMedia = postMediaRepository
                .findByPostIdInOrderBySortOrderAsc(visible.stream().map(Post::getId).toList()).stream()
                .map(PostMedia::getPostId).collect(Collectors.toSet());

        // 3b. Tác giả có điểm uy tín thấp bị giảm độ ưu tiên (bài của chính người xem không bị ảnh hưởng)
        Map<UUID, Double> reputationWeights = reputationGuard.feedWeights(
                visible.stream().map(Post::getAuthorId).filter(a -> !a.equals(viewerId)).distinct().toList());

        // 4. Chấm điểm
        List<Item> scored = new ArrayList<>(visible.size());
        for (Post p : visible) {
            double ageHours = Duration.between(p.getCreatedAt(), now).toMinutes() / 60.0;
            Signals signals = new Signals(
                    ageHours, p.getLikeCount(), p.getCommentCount(), p.getShareCount(),
                    postsWithMedia.contains(p.getId()),
                    p.getContent() == null ? 0 : p.getContent().length(),
                    viewerId.equals(p.getAuthorId()),
                    friendIds.contains(p.getAuthorId()),
                    followingIds.contains(p.getAuthorId()),
                    p.getGroupId() != null && memberGroups.contains(p.getGroupId()),
                    interactions.getOrDefault(p.getAuthorId(), 0),
                    p.isPinned());
            Scored s = PostRelevanceScorer.score(mode, signals);
            double weight = reputationWeights.getOrDefault(p.getAuthorId(), 1.0);
            scored.add(new Item(p.getId(), p.getAuthorId(), s.score() * weight, s.reason()));
        }
        // Điểm bằng nhau thì bài mới hơn trước (candidates đã sắp mới nhất trước, sort ổn định)
        scored.sort(Comparator.comparingDouble(Item::score).reversed());

        List<Item> ranked = PostRelevanceScorer.diversify(scored, RESULT_CAP);

        RankedFeedSnapshot snapshot = new RankedFeedSnapshot();
        for (Item it : ranked) {
            snapshot.getPostIds().add(it.postId().toString());
            snapshot.getScores().add(Math.round(it.score() * 1000.0) / 1000.0);
            snapshot.getReasons().add(it.reason());
        }
        log.debug("Ranked feed for {} ({}): {} candidates, {} visible, {} ranked",
                viewerId, mode, candidates.size(), visible.size(), ranked.size());
        return snapshot;
    }

    /** Bạn bè / người đang theo dõi từ user-service. Lỗi thì xếp hạng không có độ thân thiết thay vì làm hỏng bảng tin. */
    private void loadSocialGraph(Set<UUID> friendIds, Set<UUID> followingIds) {
        try {
            ApiResponse<Map<String, List<UUID>>> response = userConnectionClient.getSocialGraph();
            Map<String, List<UUID>> data = response == null ? null : response.getData();
            if (data == null) return;
            friendIds.addAll(data.getOrDefault("friendIds", Collections.emptyList()));
            followingIds.addAll(data.getOrDefault("followingIds", Collections.emptyList()));
        } catch (Exception e) {
            log.warn("Could not load social graph for feed ranking: {}", e.getMessage());
        }
    }

    /** Tổng số lần mình thả cảm xúc, bình luận, chia sẻ bài của từng tác giả trong 30 ngày qua. */
    private Map<UUID, Integer> loadInteractions(UUID viewerId, LocalDateTime now) {
        LocalDateTime since = now.minusDays(INTERACTION_WINDOW_DAYS);
        Map<UUID, Integer> totals = new HashMap<>();
        try {
            addCounts(totals, reactionRepository.countInteractionsByAuthor(viewerId, since));
            addCounts(totals, commentRepository.countInteractionsByAuthor(viewerId, since));
            addCounts(totals, postRepository.countSharesByOriginalAuthor(viewerId, since));
        } catch (Exception e) {
            log.warn("Could not load interaction affinity for feed ranking: {}", e.getMessage());
        }
        return totals;
    }

    private static void addCounts(Map<UUID, Integer> totals, List<Object[]> rows) {
        for (Object[] row : rows) {
            if (row[0] == null) continue;
            totals.merge((UUID) row[0], ((Number) row[1]).intValue(), Integer::sum);
        }
    }
}
