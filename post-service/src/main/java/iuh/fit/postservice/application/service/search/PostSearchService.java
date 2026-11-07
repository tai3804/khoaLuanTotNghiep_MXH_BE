package iuh.fit.postservice.application.service.search;

import iuh.fit.postservice.application.service.PostEmbeddingService;
import iuh.fit.postservice.application.service.PostVisibilityService;
import iuh.fit.postservice.application.service.ReputationGuard;
import iuh.fit.postservice.application.service.ranking.FeedRankingService;
import iuh.fit.postservice.application.service.ranking.PostRelevanceScorer;
import iuh.fit.postservice.application.service.search.SearchTextAnalyzer.ParsedQuery;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.enums.PostStatus;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import iuh.fit.postservice.infrastructure.persistence.search.PostLexicalIndex;
import iuh.fit.postservice.infrastructure.persistence.search.PostLexicalIndex.CorpusStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Tìm kiếm bài viết theo kiến trúc nhiều tầng (cascade) của các hệ thống tìm kiếm mạng xã hội quy mô lớn:
 *
 * <ol>
 *   <li><b>Hiểu truy vấn</b> ({@link SearchTextAnalyzer}): chuẩn hoá, bỏ dấu, tách từ khoá/hashtag, khớp tiền tố từ cuối.</li>
 *   <li><b>Truy xuất ứng viên song song</b> (giống tầng L0 của Facebook: "hybrid" = khớp từ khoá + embedding):
 *     <ul>
 *       <li>A. Chỉ mục đảo GIN (term matching) toàn bộ bài viết;</li>
 *       <li>B. Embedding ANN, pgvector HNSW cosine (Embedding-based Retrieval);</li>
 *       <li>C. Truy xuất theo đồ thị xã hội: cùng truy vấn từ khoá nhưng giới hạn trong bài của bạn bè/người theo dõi/chính mình
 *           (toán tử friends-of của Unicorn), để bài của người quen không bị chìm khi truy vấn có quá nhiều kết quả công khai.</li>
 *     </ul></li>
 *   <li><b>Trộn và cắt (L1)</b>: Reciprocal Rank Fusion ({@link RankFusion}) giữ {@value #FUSED_CUT} ứng viên.</li>
 *   <li><b>Xếp hạng (L2)</b>: BM25 ({@link Bm25}) + cosine + cụm khớp + hashtag, điều chỉnh bởi tín hiệu xã hội, tương tác,
 *       độ mới và uy tín tác giả ({@link SearchScorer}).</li>
 *   <li><b>Đa dạng hoá</b>: phạt nhiều bài liên tiếp của cùng một tác giả ({@link PostRelevanceScorer#diversify}).</li>
 * </ol>
 *
 * Quyền xem bài được áp dụng <b>trước</b> khi xếp hạng, nên tổng số kết quả và phân trang là chính xác.
 * Mỗi tầng tự suy giảm nhẹ nhàng: thiếu embedding thì chỉ còn từ khoá; thiếu chỉ mục full-text thì chỉ còn embedding.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostSearchService {

    static final int LEXICAL_LIMIT = 200;
    static final int SOCIAL_LIMIT = 100;
    static final int VECTOR_LIMIT = 150;
    static final int FUSED_CUT = 120;
    static final int RESULT_CAP = 200;

    private final PostLexicalIndex lexicalIndex;
    private final PostEmbeddingService postEmbeddingService;
    private final PostRepository postRepository;
    private final PostVisibilityService postVisibilityService;
    private final FeedRankingService feedRankingService;
    private final ReputationGuard reputationGuard;

    public record Hit(Post post, double score, String reason, double bm25, double cosine) {
    }

    /** {@code queryVector} rỗng nếu không tạo được embedding (dùng để ghi nhận ý định tìm kiếm cho bảng tin). */
    public record Result(List<Hit> hits, List<Float> queryVector) {
        static Result empty(List<Float> vector) {
            return new Result(List.of(), vector);
        }
    }

    public Result search(String rawQuery, UUID viewerId) {
        long t0 = System.nanoTime();
        ParsedQuery query = SearchTextAnalyzer.parse(rawQuery);
        if (query.isEmpty()) return Result.empty(List.of());

        // --- Hiểu truy vấn + vector truy vấn (cache) ---
        List<Float> queryVector = postEmbeddingService.getCachedQueryEmbedding(query.raw());
        CorpusStats stats = lexicalIndex.isAvailable() ? lexicalIndex.corpusStats() : new CorpusStats(0, 1.0);
        // Phân phối nền cosine của truy vấn này; không ước lượng được (corpus quá nhỏ/chưa có vector) thì bỏ nhánh ngữ nghĩa.
        PostEmbeddingService.QueryBaseline baseline = queryVector.isEmpty() ? null
                : postEmbeddingService.sampleQueryBaseline(queryVector, stats.totalDocs()).orElse(null);
        boolean hasVector = baseline != null;
        long tEmbed = System.nanoTime();

        // --- Đồ thị xã hội của người xem ---
        Set<UUID> friendIds = new HashSet<>();
        Set<UUID> followingIds = new HashSet<>();
        if (viewerId != null) feedRankingService.loadSocialGraph(friendIds, followingIds);

        long tGraph = System.nanoTime();

        // --- Truy xuất ứng viên (L0) ---
        String tsQuery = PostLexicalIndex.toTsQuery(query.terms(), query.prefixLast());
        List<UUID> lexical = lexicalIndex.match(tsQuery, LEXICAL_LIMIT, null);

        List<UUID> social = List.of();
        if (viewerId != null) {
            Set<UUID> circle = new LinkedHashSet<>(friendIds);
            circle.addAll(followingIds);
            circle.add(viewerId);
            social = lexicalIndex.match(tsQuery, SOCIAL_LIMIT, circle);
        }

        List<UUID> vector = hasVector
                ? postEmbeddingService.searchSimilar(queryVector, VECTOR_LIMIT, 0.0).stream()
                        .filter(m -> baseline.zScore(m.similarity()) >= SearchScorer.Z_CANDIDATE)
                        .map(PostEmbeddingService.PostVectorMatch::postId).toList()
                : List.of();

        long tRetrieve = System.nanoTime();

        // --- Trộn + cắt (L1) ---
        Map<UUID, Double> fused = RankFusion.reciprocalRank(List.of(lexical, vector, social));
        if (fused.isEmpty()) return Result.empty(queryVector);
        List<UUID> candidateIds = fused.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(FUSED_CUT)
                .map(Map.Entry::getKey)
                .toList();

        // --- Nạp bài, lọc trạng thái và quyền xem ---
        List<Post> posts = postRepository.findAllWithCollectionsByIdIn(candidateIds).stream()
                .filter(p -> p != null && !p.isDeleted() && !p.isArchived() && p.getStatus() == PostStatus.PUBLISHED)
                .toList();
        if (posts.isEmpty()) return Result.empty(queryVector);
        Set<UUID> visibleIds = postVisibilityService.visiblePostIds(posts, viewerId, false);
        List<Post> visible = posts.stream().filter(p -> visibleIds.contains(p.getId())).toList();
        if (visible.isEmpty()) return Result.empty(queryVector);

        long tLoad = System.nanoTime();

        // --- Đặc trưng cho L2 ---
        List<UUID> visibleIdList = visible.stream().map(Post::getId).toList();
        Map<UUID, Double> cosines = hasVector
                ? postEmbeddingService.computeSimilaritiesForPosts(visibleIdList, postEmbeddingService.toVectorString(queryVector))
                : Map.of();

        List<Double> idfs = new ArrayList<>(query.terms().size());
        for (int i = 0; i < query.terms().size(); i++) {
            boolean prefix = query.prefixLast() && i == query.terms().size() - 1;
            idfs.add(stats.totalDocs() > 0
                    ? Bm25.idf(stats.totalDocs(), lexicalIndex.docFrequency(query.terms().get(i), prefix))
                    : 0.0);
        }

        Map<UUID, Double> bm25 = new HashMap<>();
        Map<UUID, Double> phrase = new HashMap<>();
        for (Post p : visible) {
            String content = p.getContent() == null ? "" : p.getContent();
            List<String> tokens = SearchTextAnalyzer.tokens(content);
            List<String> exactTokens = SearchTextAnalyzer.exactTokens(content);
            double score = stats.totalDocs() > 0
                    ? Bm25.score(tokens, exactTokens, query.terms(), query.exactTerms(), idfs, query.prefixLast(), stats.avgDocLength())
                    : 0.0;
            bm25.put(p.getId(), score);
            phrase.put(p.getId(), phraseMatch(query, content, tokens));
        }

        Set<UUID> memberGroups = postVisibilityService.memberGroupIds(visible);
        Map<UUID, Integer> interactions = viewerId != null
                ? feedRankingService.loadInteractions(viewerId, LocalDateTime.now()) : Map.of();
        Map<UUID, Double> reputation = reputationGuard.feedWeights(
                visible.stream().map(Post::getAuthorId).filter(a -> !a.equals(viewerId)).distinct().toList());

        long tFeatures = System.nanoTime();

        // --- Xếp hạng (L2) ---
        LocalDateTime now = LocalDateTime.now();
        Map<UUID, Post> byId = new HashMap<>();
        Map<UUID, Double> finalScores = new HashMap<>();
        Map<UUID, String> reasons = new HashMap<>();
        List<PostRelevanceScorer.Item> scored = new ArrayList<>(visible.size());
        for (Post p : visible) {
            UUID id = p.getId();
            double rawBm25 = bm25.getOrDefault(id, 0.0);
            double cosine = cosines.getOrDefault(id, 0.0);
            double z = hasVector && cosines.containsKey(id) ? baseline.zScore(cosine) : -10.0;
            double hashtag = hashtagMatch(query, p);
            // Khớp từ khoá tính theo BM25 (đúng dấu nếu người dùng gõ có dấu), không theo việc trúng chỉ mục đã bỏ dấu.
            boolean lexicalHit = rawBm25 > 0;
            if (!SearchScorer.isRelevant(rawBm25, z, hashtag)) continue;

            boolean friend = friendIds.contains(p.getAuthorId());
            boolean following = followingIds.contains(p.getAuthorId());
            boolean group = p.getGroupId() != null && memberGroups.contains(p.getGroupId());
            boolean own = viewerId != null && viewerId.equals(p.getAuthorId());
            double ageHours = p.getCreatedAt() == null ? 0 : Duration.between(p.getCreatedAt(), now).toMinutes() / 60.0;

            SearchScorer.Features f = new SearchScorer.Features(
                    Bm25.normalize(rawBm25, idfs),
                    z,
                    phrase.getOrDefault(id, 0.0),
                    hashtag,
                    SearchScorer.social(own, friend, following, group, interactions.getOrDefault(p.getAuthorId(), 0)),
                    SearchScorer.engagement(p.getLikeCount(), p.getCommentCount(), p.getShareCount()),
                    SearchScorer.freshness(ageHours));
            if (SearchScorer.relevance(f) < SearchScorer.MIN_RELEVANCE) continue;
            double score = SearchScorer.score(f, reputation.getOrDefault(p.getAuthorId(), 1.0));
            String reason = SearchScorer.reason(f, lexicalHit, friend, following, group);

            byId.put(id, p);
            finalScores.put(id, score);
            reasons.put(id, reason);
            scored.add(new PostRelevanceScorer.Item(id, p.getAuthorId(), score, reason));
        }
        scored.sort(Comparator.comparingDouble(PostRelevanceScorer.Item::score).reversed());

        // --- Cắt đuôi theo ngưỡng tương đối rồi đa dạng hoá ---
        double[] sortedScores = scored.stream().mapToDouble(PostRelevanceScorer.Item::score).toArray();
        List<PostRelevanceScorer.Item> kept = scored.subList(0, SearchScorer.cutoffIndex(sortedScores, SearchScorer.RELATIVE_CUTOFF));
        List<PostRelevanceScorer.Item> ranked = PostRelevanceScorer.diversify(kept, RESULT_CAP);
        List<Hit> hits = ranked.stream()
                .map(it -> new Hit(byId.get(it.postId()), Math.round(finalScores.get(it.postId()) * 1000.0) / 1000.0,
                        reasons.get(it.postId()), bm25.getOrDefault(it.postId(), 0.0), cosines.getOrDefault(it.postId(), 0.0)))
                .toList();
        long tEnd = System.nanoTime();
        log.debug("Search '{}' viewer={}: lexical={} social={} vector={} fused={} visible={} scored={} kept={} ranked={} | ms: embed={} graph={} retrieve={} load={} features={} rank={}",
                query.raw(), viewerId, lexical.size(), social.size(), vector.size(), candidateIds.size(), visible.size(),
                scored.size(), kept.size(), hits.size(),
                (tEmbed - t0) / 1_000_000, (tGraph - tEmbed) / 1_000_000, (tRetrieve - tGraph) / 1_000_000,
                (tLoad - tRetrieve) / 1_000_000, (tFeatures - tLoad) / 1_000_000, (tEnd - tFeatures) / 1_000_000);
        return new Result(hits, queryVector);
    }

    /** 1,0 nếu nội dung chứa nguyên cụm truy vấn đúng dấu; 0,6 nếu chỉ khớp sau khi bỏ dấu (theo ranh giới từ); 0 nếu không. */
    static double phraseMatch(ParsedQuery query, String content, List<String> docTokens) {
        if (content.isEmpty() || query.exactPhrase().isEmpty()) return 0.0;
        String exactContent = Normalizer.normalize(content, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        if (exactContent.contains(query.exactPhrase())) return SearchScorer.PHRASE_EXACT;
        String joined = " " + String.join(" ", docTokens);
        if (!query.foldedPhrase().isEmpty() && joined.contains(" " + query.foldedPhrase())) return SearchScorer.PHRASE_FOLDED;
        return 0.0;
    }

    /** 1,0 nếu bài gắn đúng hashtag có trong truy vấn; 0,5 nếu một từ khoá thường trùng hashtag của bài. */
    static double hashtagMatch(ParsedQuery query, Post post) {
        if (post.getHashtags() == null || post.getHashtags().isEmpty()) return 0.0;
        Set<String> tags = post.getHashtags().stream()
                .map(t -> SearchTextAnalyzer.fold(t).replaceAll("[^a-z0-9]", ""))
                .collect(Collectors.toSet());
        for (String h : query.hashtags()) {
            if (tags.contains(h)) return 1.0;
        }
        for (String t : query.terms()) {
            if (tags.contains(t)) return 0.5;
        }
        return 0.0;
    }
}
