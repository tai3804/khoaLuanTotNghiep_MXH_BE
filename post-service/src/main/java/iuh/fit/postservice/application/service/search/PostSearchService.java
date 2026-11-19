package iuh.fit.postservice.application.service.search;

import iuh.fit.postservice.application.service.PostEmbeddingService;
import iuh.fit.postservice.application.service.PostVisibilityService;
import iuh.fit.postservice.application.service.ReputationGuard;
import iuh.fit.postservice.application.service.ranking.FeedRankingService;
import iuh.fit.postservice.application.service.ranking.MmrReranker;
import iuh.fit.postservice.application.service.ranking.PostRelevanceScorer;
import iuh.fit.postservice.application.service.search.SearchScorer.Weights;
import iuh.fit.postservice.application.service.search.SearchTextAnalyzer.ParsedQuery;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.enums.PostStatus;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tìm kiếm bài viết theo <b>ngữ nghĩa</b> (embedding) kết hợp đồ thị xã hội, theo kiến trúc nhiều tầng. Không dùng chỉ mục
 * toàn văn (tsvector/GIN) hay BM25: sức mạnh nằm ở không gian vector, đồ thị quan hệ và xếp hạng học được.
 *
 * <ol>
 *   <li><b>Hiểu truy vấn</b> ({@link SearchTextAnalyzer}): cụm có dấu / không dấu, hashtag.</li>
 *   <li><b>Truy xuất ứng viên song song</b>:
 *     <ul>
 *       <li>A. Embedding ANN toàn cục, pgvector HNSW cosine (Embedding-based Retrieval);</li>
 *       <li>B. Nhánh đồ thị xã hội: cosine <i>chính xác</i> trên các bài gần đây của bạn bè/người theo dõi/chính mình, để bài
 *           của người quen không bị chìm khi có quá nhiều bài công khai gần nghĩa hơn một chút;</li>
 *       <li>C. Hashtag trong truy vấn (khớp chính xác qua bảng hashtag).</li>
 *     </ul></li>
 *   <li><b>Trộn và cắt (L1)</b>: Reciprocal Rank Fusion ({@link RankFusion}) giữ {@value #FUSED_CUT} ứng viên.</li>
 *   <li><b>Xếp hạng (L2)</b>: ngữ nghĩa (z-score theo phân phối nền của chính truy vấn) + cụm khớp + hashtag, điều chỉnh bởi
 *       tín hiệu xã hội, tương tác, độ mới và uy tín tác giả, với bộ trọng số <i>học được</i> ({@link SearchScorer.Weights}).</li>
 *   <li><b>Đa dạng hoá</b>: MMR trên embedding ({@link MmrReranker}); không có vector thì phạt theo tác giả.</li>
 * </ol>
 *
 * Quyền xem bài được áp dụng <b>trước</b> khi xếp hạng, nên tổng số kết quả và phân trang là chính xác. Thiếu embedding của
 * truy vấn (ai-service lỗi) thì chỉ còn nhánh hashtag.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostSearchService {

    static final int VECTOR_LIMIT = 150;
    static final int SOCIAL_LIMIT = 100;
    static final int SOCIAL_POOL = 300;
    static final int SOCIAL_WINDOW_DAYS = 90;
    static final int HASHTAG_LIMIT = 50;
    static final int FUSED_CUT = 120;
    static final int RESULT_CAP = 200;

    private final PostEmbeddingService postEmbeddingService;
    private final PostRepository postRepository;
    private final PostVisibilityService postVisibilityService;
    private final FeedRankingService feedRankingService;
    private final ReputationGuard reputationGuard;
    private final SearchWeightsProvider weightsProvider;

    /** Một ứng viên đã truy xuất, kèm đặc trưng xếp hạng. */
    public record Candidate(Post post, SearchScorer.Features features, double authorWeight, double rrfScore, double cosine,
                            boolean friend, boolean following, boolean memberGroup) {
    }

    /** Kết quả giai đoạn truy xuất và trích đặc trưng; {@code queryVector} rỗng nếu không tạo được embedding. */
    public record Collected(List<Candidate> candidates, List<Float> queryVector) {
        static Collected empty(List<Float> vector) {
            return new Collected(List.of(), vector);
        }
    }

    public record Hit(Post post, double score, String reason, double cosine) {
    }

    public record Result(List<Hit> hits, List<Float> queryVector) {
    }

    public Result search(String rawQuery, UUID viewerId) {
        Collected collected = collect(rawQuery, viewerId);
        if (collected.candidates().isEmpty()) return new Result(List.of(), collected.queryVector());
        return new Result(rank(collected.candidates(), weightsProvider.current()), collected.queryVector());
    }

    /**
     * Truy xuất (A + B + C), hợp nhất RRF, lọc quyền xem và trích đặc trưng. Tách riêng khỏi bước chấm điểm để bộ đánh giá offline
     * ({@code SearchEvaluator}) có thể chấm lại cùng một tập ứng viên bằng nhiều bộ trọng số khác nhau.
     */
    public Collected collect(String rawQuery, UUID viewerId) {
        ParsedQuery query = SearchTextAnalyzer.parse(rawQuery);
        if (query.isEmpty()) return Collected.empty(List.of());

        // --- Vector truy vấn (cache) và phân phối nền cosine của truy vấn này ---
        List<Float> queryVector = postEmbeddingService.getCachedQueryEmbedding(query.raw());
        PostEmbeddingService.QueryBaseline baseline = queryVector.isEmpty() ? null
                : postEmbeddingService.sampleQueryBaseline(queryVector, postRepository.countByDeletedFalse()).orElse(null);
        boolean hasVector = baseline != null;

        // --- Đồ thị xã hội của người xem ---
        Set<UUID> friendIds = new HashSet<>();
        Set<UUID> followingIds = new HashSet<>();
        if (viewerId != null) feedRankingService.loadSocialGraph(friendIds, followingIds);

        // --- Truy xuất ứng viên (L0) ---
        List<UUID> global = List.of();
        List<UUID> social = List.of();
        if (hasVector) {
            global = postEmbeddingService.searchSimilar(queryVector, VECTOR_LIMIT, 0.0).stream()
                    .filter(m -> baseline.zScore(m.similarity()) >= SearchScorer.Z_CANDIDATE)
                    .map(PostEmbeddingService.PostVectorMatch::postId).toList();
            if (viewerId != null) {
                social = socialCandidates(queryVector, baseline, friendIds, followingIds, viewerId);
            }
        }
        List<UUID> hashtag = hashtagCandidates(query);

        // --- Trộn + cắt (L1) ---
        Map<UUID, Double> fused = RankFusion.reciprocalRank(List.of(global, social, hashtag));
        if (fused.isEmpty()) return Collected.empty(queryVector);
        List<UUID> candidateIds = fused.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(FUSED_CUT)
                .map(Map.Entry::getKey)
                .toList();

        // --- Nạp bài, lọc trạng thái và quyền xem ---
        List<Post> posts = postRepository.findAllWithCollectionsByIdIn(candidateIds).stream()
                .filter(p -> p != null && !p.isDeleted() && !p.isArchived() && p.getStatus() == PostStatus.PUBLISHED)
                .toList();
        if (posts.isEmpty()) return Collected.empty(queryVector);
        Set<UUID> visibleIds = postVisibilityService.visiblePostIds(posts, viewerId, false);
        List<Post> visible = posts.stream().filter(p -> visibleIds.contains(p.getId())).toList();
        if (visible.isEmpty()) return Collected.empty(queryVector);

        // --- Đặc trưng cho L2 ---
        List<UUID> visibleIdList = visible.stream().map(Post::getId).toList();
        Map<UUID, Double> cosines = hasVector
                ? postEmbeddingService.computeSimilaritiesForPosts(visibleIdList, postEmbeddingService.toVectorString(queryVector))
                : Map.of();
        Set<UUID> memberGroups = postVisibilityService.memberGroupIds(visible);
        LocalDateTime now = LocalDateTime.now();
        Map<UUID, Integer> interactions = viewerId != null ? feedRankingService.loadInteractions(viewerId, now) : Map.of();
        Map<UUID, Double> reputation = reputationGuard.feedWeights(
                visible.stream().map(Post::getAuthorId).filter(a -> !a.equals(viewerId)).distinct().toList());

        List<Candidate> out = new ArrayList<>(visible.size());
        for (Post p : visible) {
            UUID id = p.getId();
            double cosine = cosines.getOrDefault(id, 0.0);
            double z = hasVector && cosines.containsKey(id) ? baseline.zScore(cosine) : -10.0;
            double phrase = phraseMatch(query, p.getContent() == null ? "" : p.getContent());
            double hashtagMatch = hashtagMatch(query, p);
            if (!SearchScorer.isRelevant(phrase, z, hashtagMatch)) continue;

            boolean friend = friendIds.contains(p.getAuthorId());
            boolean following = followingIds.contains(p.getAuthorId());
            boolean group = p.getGroupId() != null && memberGroups.contains(p.getGroupId());
            boolean own = viewerId != null && viewerId.equals(p.getAuthorId());
            double ageHours = p.getCreatedAt() == null ? 0 : Duration.between(p.getCreatedAt(), now).toMinutes() / 60.0;

            SearchScorer.Features f = new SearchScorer.Features(
                    z, phrase, hashtagMatch,
                    SearchScorer.social(own, friend, following, group, interactions.getOrDefault(p.getAuthorId(), 0)),
                    SearchScorer.engagement(p.getLikeCount(), p.getCommentCount(), p.getShareCount()),
                    SearchScorer.freshness(ageHours));
            out.add(new Candidate(p, f, reputation.getOrDefault(p.getAuthorId(), 1.0), fused.getOrDefault(id, 0.0),
                    cosine, friend, following, group));
        }
        log.debug("Search '{}' viewer={}: global={} social={} hashtag={} fused={} visible={} candidates={}",
                query.raw(), viewerId, global.size(), social.size(), hashtag.size(), candidateIds.size(), visible.size(), out.size());
        return new Collected(out, queryVector);
    }

    /** Chấm điểm bằng bộ trọng số {@code w}, cắt đuôi rồi đa dạng hoá bằng MMR. */
    List<Hit> rank(List<Candidate> candidates, Weights w) {
        Map<UUID, Candidate> byId = new HashMap<>();
        Map<UUID, String> reasons = new HashMap<>();
        List<PostRelevanceScorer.Item> scored = new ArrayList<>(candidates.size());
        for (Candidate c : candidates) {
            if (SearchScorer.relevance(c.features(), w) < SearchScorer.MIN_RELEVANCE) continue;
            UUID id = c.post().getId();
            double score = SearchScorer.score(c.features(), c.authorWeight(), w);
            String reason = SearchScorer.reason(c.features(), c.friend(), c.following(), c.memberGroup());
            byId.put(id, c);
            reasons.put(id, reason);
            scored.add(new PostRelevanceScorer.Item(id, c.post().getAuthorId(), score, reason));
        }
        scored.sort(Comparator.comparingDouble(PostRelevanceScorer.Item::score).reversed());

        double[] sortedScores = scored.stream().mapToDouble(PostRelevanceScorer.Item::score).toArray();
        List<PostRelevanceScorer.Item> kept = scored.subList(0, SearchScorer.cutoffIndex(sortedScores, SearchScorer.RELATIVE_CUTOFF));

        Map<UUID, float[]> vectors = postEmbeddingService.getEmbeddings(kept.stream().map(PostRelevanceScorer.Item::postId).toList());
        List<PostRelevanceScorer.Item> ranked = vectors.isEmpty()
                ? PostRelevanceScorer.diversify(kept, RESULT_CAP)
                : MmrReranker.rerank(kept, vectors, MmrReranker.DEFAULT_LAMBDA, RESULT_CAP);

        return ranked.stream()
                .map(it -> new Hit(byId.get(it.postId()).post(), Math.round(it.score() * 1000.0) / 1000.0,
                        reasons.get(it.postId()), byId.get(it.postId()).cosine()))
                .toList();
    }

    /**
     * Nhánh đồ thị xã hội: cosine chính xác (không xấp xỉ) trên các bài {@value #SOCIAL_WINDOW_DAYS} ngày gần nhất của tối đa
     * {@value #SOCIAL_POOL} bài thuộc vòng quan hệ. Tập này nhỏ nên tính chính xác trong cơ sở dữ liệu, tránh bài của người quen
     * bị HNSW toàn cục bỏ sót khi có nhiều bài công khai gần nghĩa hơn một chút.
     */
    private List<UUID> socialCandidates(List<Float> queryVector, PostEmbeddingService.QueryBaseline baseline,
                                        Set<UUID> friendIds, Set<UUID> followingIds, UUID viewerId) {
        try {
            Set<UUID> circle = new LinkedHashSet<>(friendIds);
            circle.addAll(followingIds);
            circle.add(viewerId);
            List<UUID> recent = postRepository.findRecentIdsByAuthors(
                    circle, LocalDateTime.now().minusDays(SOCIAL_WINDOW_DAYS), PageRequest.of(0, SOCIAL_POOL));
            if (recent.isEmpty()) return List.of();
            Map<UUID, Double> sims = postEmbeddingService.computeSimilaritiesForPosts(
                    recent, postEmbeddingService.toVectorString(queryVector));
            return sims.entrySet().stream()
                    .filter(e -> baseline.zScore(e.getValue()) >= SearchScorer.Z_CANDIDATE)
                    .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                    .limit(SOCIAL_LIMIT)
                    .map(Map.Entry::getKey)
                    .toList();
        } catch (Exception e) {
            log.warn("Social-graph retrieval skipped: {}", e.getMessage());
            return List.of();
        }
    }

    /** Nhánh hashtag: bài gắn đúng hashtag có trong truy vấn, mới nhất trước. */
    private List<UUID> hashtagCandidates(ParsedQuery query) {
        if (query.hashtagTags().isEmpty()) return List.of();
        Set<UUID> ids = new LinkedHashSet<>();
        try {
            for (String tag : query.hashtagTags()) {
                postRepository.findByHashtag(tag, PageRequest.of(0, HASHTAG_LIMIT)).forEach(p -> ids.add(p.getId()));
            }
        } catch (Exception e) {
            log.warn("Hashtag retrieval skipped: {}", e.getMessage());
        }
        return new ArrayList<>(ids);
    }

    /** 1,0 nếu nội dung chứa nguyên cụm truy vấn đúng dấu; 0,6 nếu chỉ khớp sau khi bỏ dấu (theo ranh giới từ); 0 nếu không. */
    static double phraseMatch(ParsedQuery query, String content) {
        if (content.isEmpty() || query.exactPhrase().isEmpty()) return 0.0;
        String exactContent = Normalizer.normalize(content, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        if (exactContent.contains(query.exactPhrase())) return SearchScorer.PHRASE_EXACT;
        String joined = " " + String.join(" ", SearchTextAnalyzer.tokens(content));
        if (!query.foldedPhrase().isEmpty() && joined.contains(" " + query.foldedPhrase())) return SearchScorer.PHRASE_FOLDED;
        return 0.0;
    }

    /** 1,0 nếu bài gắn đúng hashtag có trong truy vấn; 0 nếu không. */
    static double hashtagMatch(ParsedQuery query, Post post) {
        if (post.getHashtags() == null || post.getHashtags().isEmpty() || query.hashtags().isEmpty()) return 0.0;
        Set<String> tags = post.getHashtags().stream()
                .map(t -> SearchTextAnalyzer.fold(t).replaceAll("[^a-z0-9]", ""))
                .collect(Collectors.toSet());
        for (String h : query.hashtags()) {
            if (tags.contains(h)) return 1.0;
        }
        return 0.0;
    }
}
