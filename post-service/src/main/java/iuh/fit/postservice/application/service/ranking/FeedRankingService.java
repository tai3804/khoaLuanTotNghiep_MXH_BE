package iuh.fit.postservice.application.service.ranking;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.postservice.application.service.PostEmbeddingService;
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
    private static final int CF_WINDOW_DAYS = 60;
    private static final int CF_ROW_LIMIT = 20_000;
    private static final long INTERACTION_CACHE_TTL_MS = 120_000L;
    private static final int MMR_POOL = 120;

    private final PostRepository postRepository;
    private final PostMediaRepository postMediaRepository;
    private final ReactionRepository reactionRepository;
    private final CommentRepository commentRepository;
    private final PostVisibilityService postVisibilityService;
    private final UserConnectionClient userConnectionClient;
    private final RankedFeedCache rankedFeedCache;
    private final PostSeenService postSeenService;
    private final ReputationGuard reputationGuard;
    private final PostEmbeddingService postEmbeddingService;

    public RankedFeedSnapshot snapshot(UUID viewerId, Mode mode) {
        return snapshot(viewerId, mode, false, false);
    }

    /**
     * @param unseenOnly   bỏ các bài người xem đã xem (kể cả bài của chính họ); lưu bản chụp riêng với bản đầy đủ
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

        // 2b. Bài đã xem rồi thì không đưa lại (kể cả bài của chính mình)
        if (unseenOnly && !visible.isEmpty()) {
            Set<UUID> seen = postSeenService.seenAmong(viewerId, visible.stream().map(Post::getId).toList());
            if (!seen.isEmpty()) {
                visible = visible.stream()
                        .filter(p -> !seen.contains(p.getId()))
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

        // 3c. Ý định tìm kiếm gần đây (Search-biased Retargeting)
        String searchInterestTopic = null;
        Map<UUID, Double> searchSimilarities = Collections.emptyMap();
        if (viewerId != null && mode == Mode.RELEVANCE) {
            try {
                Optional<PostEmbeddingService.UserSearchInterest> interestOpt = postEmbeddingService.getRecentSearchInterest(viewerId, 24);
                if (interestOpt.isPresent()) {
                    searchInterestTopic = interestOpt.get().queryText();
                    searchSimilarities = postEmbeddingService.computeSimilaritiesForPosts(
                            visible.stream().map(Post::getId).toList(),
                            interestOpt.get().vectorStr());
                    log.debug("User {} recent search interest: '{}', matched {} posts",
                            viewerId, searchInterestTopic, searchSimilarities.size());
                }
            } catch (Exception e) {
                log.warn("Failed to apply search-biased retargeting for user {}: {}", viewerId, e.getMessage());
            }
        }

        // 3d. Tín hiệu từ lịch sử tương tác: lọc cộng tác theo bài viết (Item-based CF) và hồ sơ sở thích (theo nội dung).
        // Người xem mới chưa đủ tương tác thì không có hai điểm này.
        Map<UUID, Double> cfScores = Collections.emptyMap();
        Map<UUID, Double> contentBoosts = Collections.emptyMap();
        if (viewerId != null && mode == Mode.RELEVANCE) {
            List<UUID> candidateIds = visible.stream().map(Post::getId).toList();
            List<ItemCollaborativeFilter.Interaction> interactionData = loadInteractionData(now);
            cfScores = collaborativeScores(viewerId, candidateIds, interactionData, now);
            contentBoosts = contentBoosts(viewerId, candidateIds, interactionData, now);
        }

        // 4. Chấm điểm
        List<Item> scored = new ArrayList<>(visible.size());
        for (Post p : visible) {
            double ageHours = Duration.between(p.getCreatedAt(), now).toMinutes() / 60.0;
            double searchSim = searchSimilarities.getOrDefault(p.getId(), 0.0);
            Signals signals = new Signals(
                    ageHours, p.getLikeCount(), p.getCommentCount(), p.getShareCount(),
                    postsWithMedia.contains(p.getId()),
                    p.getContent() == null ? 0 : p.getContent().length(),
                    viewerId.equals(p.getAuthorId()),
                    friendIds.contains(p.getAuthorId()),
                    followingIds.contains(p.getAuthorId()),
                    p.getGroupId() != null && memberGroups.contains(p.getGroupId()),
                    interactions.getOrDefault(p.getAuthorId(), 0),
                    p.isPinned(),
                    searchSim,
                    searchInterestTopic,
                    cfScores.getOrDefault(p.getId(), 0.0),
                    contentBoosts.getOrDefault(p.getId(), 0.0));
            Scored s = PostRelevanceScorer.score(mode, signals);
            double weight = reputationWeights.getOrDefault(p.getAuthorId(), 1.0);
            scored.add(new Item(p.getId(), p.getAuthorId(), s.score() * weight, s.reason()));
        }
        // Điểm bằng nhau thì bài mới hơn trước (candidates đã sắp mới nhất trước, sort ổn định)
        scored.sort(Comparator.comparingDouble(Item::score).reversed());

        List<Item> ranked = mode == Mode.RELEVANCE
                ? diversifyByContent(scored)
                : PostRelevanceScorer.diversify(scored, RESULT_CAP);

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

    /**
     * Đa dạng hoá bảng tin theo <b>nội dung</b> bằng MMR trên embedding cho {@value #MMR_POOL} bài đầu (phần tạo nên những màn hình
     * đầu tiên người dùng thấy), phần đuôi chỉ phạt theo tác giả. Không có vector (pgvector tắt/lỗi) thì quay về phạt theo tác giả
     * cho cả danh sách, nên bảng tin không bao giờ hỏng vì bước này.
     */
    private List<Item> diversifyByContent(List<Item> scored) {
        int pool = Math.min(scored.size(), MMR_POOL);
        List<Item> head = scored.subList(0, pool);
        Map<UUID, float[]> vectors;
        try {
            vectors = postEmbeddingService.getEmbeddings(head.stream().map(Item::postId).toList());
        } catch (Exception e) {
            log.warn("MMR skipped, could not load embeddings: {}", e.getMessage());
            vectors = Map.of();
        }
        if (vectors.isEmpty()) return PostRelevanceScorer.diversify(scored, RESULT_CAP);

        List<Item> ranked = new ArrayList<>(MmrReranker.rerank(head, vectors, MmrReranker.DEFAULT_LAMBDA, pool));
        if (scored.size() > pool && ranked.size() < RESULT_CAP) {
            ranked.addAll(PostRelevanceScorer.diversify(scored.subList(pool, scored.size()), RESULT_CAP - ranked.size()));
        }
        return ranked;
    }

    private record CachedInteractions(List<ItemCollaborativeFilter.Interaction> data, long loadedAtMillis) {
    }

    private volatile CachedInteractions interactionCache;

    /**
     * Tương tác gần đây của mọi người dùng (thích, bình luận, chia sẻ), dùng chung cho CF và hồ sơ sở thích.
     *
     * <p>Tập này giống nhau với mọi người xem (chỉ khác ở bước chấm điểm), nhưng trước đây được nạp lại cho MỖI lần tính bảng tin:
     * 3 truy vấn, tối đa {@code 3 × CF_ROW_LIMIT} dòng. Nay giữ trong bộ nhớ {@value #INTERACTION_CACHE_TTL_MS} ms và dùng chung;
     * độ trễ vài phút của tín hiệu cộng tác (vốn dùng nửa đời 14 ngày) là không đáng kể. Lỗi nạp thì không cache.
     */
    List<ItemCollaborativeFilter.Interaction> loadInteractionData(LocalDateTime now) {
        CachedInteractions cached = interactionCache;
        long nowMillis = System.currentTimeMillis();
        if (cached != null && nowMillis - cached.loadedAtMillis() < INTERACTION_CACHE_TTL_MS) {
            return cached.data();
        }
        synchronized (this) {
            // Kiểm tra lại: luồng khác có thể vừa nạp xong trong lúc ta chờ khoá (tránh nhiều luồng cùng truy vấn một lúc)
            cached = interactionCache;
            if (cached != null && System.currentTimeMillis() - cached.loadedAtMillis() < INTERACTION_CACHE_TTL_MS) {
                return cached.data();
            }
            List<ItemCollaborativeFilter.Interaction> data = new ArrayList<>();
            try {
                LocalDateTime since = now.minusDays(CF_WINDOW_DAYS);
                PageRequest page = PageRequest.of(0, CF_ROW_LIMIT);
                collectRows(data, reactionRepository.findRecentInteractionRows(since, page), ItemCollaborativeFilter.W_REACTION);
                collectRows(data, commentRepository.findRecentInteractionRows(since, page), ItemCollaborativeFilter.W_COMMENT);
                collectRows(data, postRepository.findRecentShareRows(since, page), ItemCollaborativeFilter.W_SHARE);
                List<ItemCollaborativeFilter.Interaction> immutable = List.copyOf(data);
                interactionCache = new CachedInteractions(immutable, System.currentTimeMillis());
                return immutable;
            } catch (Exception e) {
                log.warn("Could not load interaction data for recommendation signals: {}", e.getMessage());
                return data;
            }
        }
    }

    /** Điểm lọc cộng tác cho các bài ứng viên. Lỗi thì bỏ qua tín hiệu này thay vì làm hỏng bảng tin. */
    Map<UUID, Double> collaborativeScores(UUID viewerId, List<UUID> candidateIds,
                                          List<ItemCollaborativeFilter.Interaction> data, LocalDateTime now) {
        try {
            return ItemCollaborativeFilter.score(viewerId, candidateIds, data, now);
        } catch (Exception e) {
            log.warn("Could not compute collaborative filtering scores: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Mức giống hồ sơ sở thích của người xem (đề xuất theo nội dung). Hồ sơ là trung bình có trọng số vector của các bài
     * người xem từng tương tác và các câu người xem từng tìm kiếm; bài người xem đã tương tác rồi không được cộng điểm lại.
     */
    Map<UUID, Double> contentBoosts(UUID viewerId, List<UUID> candidateIds,
                                    List<ItemCollaborativeFilter.Interaction> data, LocalDateTime now) {
        try {
            List<UserInterestProfile.Weighted> items = UserInterestProfile.aggregate(viewerId, data, now);
            List<UserInterestProfile.WeightedVector> searches = UserInterestProfile.searchWeights(
                    postEmbeddingService.getSearchHistory(viewerId, now.minusDays(CF_WINDOW_DAYS), UserInterestProfile.MAX_SEARCHES), now);
            if (items.size() + searches.size() < UserInterestProfile.MIN_ITEMS) return Collections.emptyMap();
            Map<UUID, float[]> embeddings = postEmbeddingService.getEmbeddings(
                    items.stream().map(UserInterestProfile.Weighted::postId).toList());
            Optional<float[]> profile = UserInterestProfile.centroid(items, embeddings, searches);
            if (profile.isEmpty()) return Collections.emptyMap();
            Set<UUID> already = UserInterestProfile.postIds(items);
            List<UUID> toScore = candidateIds.stream().filter(id -> !already.contains(id)).toList();
            Map<UUID, Double> sims = postEmbeddingService.computeSimilaritiesForPosts(
                    toScore, UserInterestProfile.toVectorString(profile.get()));
            Map<UUID, Double> boosts = new HashMap<>();
            sims.forEach((id, sim) -> {
                double b = UserInterestProfile.boost(sim);
                if (b > 0) boosts.put(id, b);
            });
            return boosts;
        } catch (Exception e) {
            log.warn("Could not compute content-based interest profile: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static void collectRows(List<ItemCollaborativeFilter.Interaction> out, List<Object[]> rows, double weight) {
        for (Object[] r : rows) {
            if (r[0] == null || r[1] == null || r[2] == null) continue;
            out.add(new ItemCollaborativeFilter.Interaction((UUID) r[0], (UUID) r[1], weight, (LocalDateTime) r[2]));
        }
    }

    /** Bạn bè / người đang theo dõi từ user-service. Lỗi thì xếp hạng không có độ thân thiết thay vì làm hỏng bảng tin. */
    public void loadSocialGraph(Set<UUID> friendIds, Set<UUID> followingIds) {
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
    public Map<UUID, Integer> loadInteractions(UUID viewerId, LocalDateTime now) {
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
