package iuh.fit.postservice.application.service.insight;

import iuh.fit.postservice.application.service.PostEmbeddingService;
import iuh.fit.postservice.application.service.ranking.ItemCollaborativeFilter;
import iuh.fit.postservice.application.service.ranking.ItemCollaborativeFilter.Interaction;
import iuh.fit.postservice.application.service.ranking.UserInterestProfile;
import iuh.fit.postservice.application.service.topic.TopicClusterer;
import iuh.fit.postservice.application.util.TagAndHashtagHelper;
import iuh.fit.postservice.infrastructure.persistence.repository.CommentRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.ReactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bản đồ sở thích: biểu diễn vector sở thích 768 chiều của người dùng (chính vector mà bảng tin dùng để cộng điểm theo nội
 * dung, xem {@link UserInterestProfile}) trên cùng mặt phẳng với các bài viết và các cụm chủ đề.
 *
 * Mọi thứ được query và tính lại ở mỗi lần gọi (không cache); muốn xem dữ liệu mới thì tải lại trang.
 * <pre>
 * Nền bản đồ:
 *   mẫu ≤ 400 bài công khai gần đây có vector → PCA 2 trục ({@link PcaProjector}) → toạ độ từng bài
 *   gom cụm chủ đề bằng k-means cầu ({@link TopicClusterer}), nhãn = hashtag phổ biến hoặc đoạn đầu bài đại diện
 * Phần của người dùng:
 *   tương tác 60 ngày (thích 1, bình luận 2, chia sẻ 3, tìm kiếm 1; nửa đời 30 ngày) → vector sở thích = tâm có trọng số
 *   → chiếu lên cùng cơ sở; quỹ đạo = vector sở thích sau mỗi tương tác gần nhất
 *   → mức gần từng chủ đề (cosine) và tỉ trọng tương tác theo chủ đề
 *   → bài bảng tin sẽ ưu tiên: bài gần câu tìm kiếm trong 24 giờ (cosine ≥ 0,40, như bảng tin) và bài gần vector sở thích
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterestMapService {

    static final int SAMPLE_LIMIT = 400;
    static final int SAMPLE_WINDOW_DAYS = 180;
    static final int INTERACTION_WINDOW_DAYS = 60;
    static final int MAX_EVENTS = 300;
    static final int TRAIL_STEPS = 15;
    static final int RECOMMEND_LIMIT = 5;
    static final int MIN_TOPIC_POSTS = 6;
    static final int MAX_TOPICS = 8;
    static final int RECENT_HOURS = 24;
    /** Khớp với bảng tin: câu tìm trong 24 giờ, bài có cosine ≥ 0,40 với câu tìm được cộng điểm (PostRelevanceScorer). */
    static final int SEARCH_RECENT_HOURS = 24;
    static final double SEARCH_SIM_FLOOR = 0.40;
    static final int SEARCH_RECOMMEND_LIMIT = 3;
    static final String SEARCH = "SEARCH";

    private final PostRepository postRepository;
    private final ReactionRepository reactionRepository;
    private final CommentRepository commentRepository;
    private final PostEmbeddingService postEmbeddingService;

    /** {@code recent}: bài đăng trong {@link #RECENT_HOURS} giờ gần đây. */
    public record PostPoint(UUID id, double x, double y, int topic, String snippet, boolean recent) {
    }

    public record TopicInfo(int id, String label, List<String> hashtags, int posts, double x, double y,
                            double affinity, double share) {
    }

    public record InteractedPost(UUID id, double x, double y, int topic, String action, int count, double weight,
                                 String snippet, LocalDateTime lastAt) {
    }

    /** Một bước của quỹ đạo; {@code postId} khi bước đó là tương tác với bài, {@code query} khi là tìm kiếm. */
    public record TrailPoint(double x, double y, LocalDateTime at, String action, UUID postId, String query) {
    }

    /** Một câu tìm kiếm chiếu lên bản đồ, {@code weight} là trọng số đã suy giảm. */
    public record SearchPoint(String query, double x, double y, int topic, double weight, LocalDateTime at) {
    }

    /**
     * Bài bảng tin sẽ ưu tiên. {@code source} = SEARCH: gần câu tìm {@code query} trong 24 giờ; INTEREST: gần vector sở thích
     * ({@code boost} là mức cộng điểm theo nội dung).
     */
    public record Recommendation(UUID id, String snippet, double similarity, double boost, int topic, String source, String query) {
    }

    public record InterestMap(int dimensions,
                              double explainedX, double explainedY,
                              List<PostPoint> posts, List<TopicInfo> topics,
                              boolean hasProfile, int interactionCount, int vectorizedPosts, int minPosts,
                              Double userX, Double userY, float[] vector,
                              List<InteractedPost> interacted, List<SearchPoint> searches, List<TrailPoint> trail,
                              List<Recommendation> recommendations, LocalDateTime computedAt) {
    }

    /**
     * Một tương tác của người dùng: loại (REACTION/COMMENT/SHARE/SEARCH), trọng số, thời điểm; với bài thì có {@code postId},
     * với tìm kiếm thì có {@code query} và {@code vector} của câu tìm.
     */
    record Event(UUID postId, String action, double weight, LocalDateTime at, String query, float[] vector) {
        boolean isSearch() {
            return SEARCH.equals(action);
        }
    }

    /** Hồ sơ sở thích tại một thời điểm, cùng các thành phần đã dùng để dựng. */
    private record Profile(Optional<float[]> vector, List<UserInterestProfile.Weighted> items, int searches) {
    }

    private record TopicModel(int id, String label, List<String> hashtags, int posts, float[] centroid, double x, double y) {
    }

    private record MapBasis(PcaProjector.Basis pca, List<PostPoint> posts, Map<UUID, float[]> unitVectors, List<TopicModel> topics) {
    }

    public InterestMap build(UUID userId) {
        LocalDateTime now = LocalDateTime.now();
        MapBasis basis = buildBasis(now);
        List<Event> events = loadEvents(userId, now);

        if (basis == null) {
            return new InterestMap(0, 0, 0, List.of(), List.of(), false, events.size(), 0,
                    UserInterestProfile.MIN_ITEMS, null, null, null, List.of(), List.of(), List.of(), List.of(), now);
        }

        Set<UUID> eventPostIds = events.stream().filter(e -> !e.isSearch()).map(Event::postId)
                .collect(Collectors.toCollection(HashSet::new));
        Map<UUID, float[]> embeddings = new HashMap<>(postEmbeddingService.getEmbeddings(eventPostIds));

        Profile current = profileAt(userId, events, embeddings, now);
        Optional<float[]> profile = current.vector();
        List<UserInterestProfile.Weighted> items = current.items();
        int vectorized = (int) items.stream().filter(w -> embeddings.containsKey(w.postId())).count() + current.searches();

        List<InteractedPost> interacted = interactedPosts(basis, items, events, embeddings);
        List<SearchPoint> searches = searchPoints(basis, events, now);
        List<TrailPoint> trail = trail(userId, basis, events, embeddings);

        List<TopicInfo> topics = new ArrayList<>();
        Map<Integer, Double> shareByTopic = new HashMap<>();
        double totalWeight = interacted.stream().mapToDouble(InteractedPost::weight).sum()
                + searches.stream().mapToDouble(SearchPoint::weight).sum();
        for (InteractedPost p : interacted) shareByTopic.merge(p.topic(), p.weight(), Double::sum);
        for (SearchPoint s : searches) shareByTopic.merge(s.topic(), s.weight(), Double::sum);
        for (TopicModel t : basis.topics()) {
            double affinity = profile.map(v -> dot(v, t.centroid())).orElse(0.0);
            double share = totalWeight > 0 ? shareByTopic.getOrDefault(t.id(), 0.0) / totalWeight : 0;
            topics.add(new TopicInfo(t.id(), t.label(), t.hashtags(), t.posts(), t.x(), t.y(), round(affinity, 4), round(share, 4)));
        }

        Double userX = null, userY = null;
        float[] vector = null;
        List<Recommendation> recommendations = new ArrayList<>();
        Set<UUID> already = UserInterestProfile.postIds(items);
        // Bài gần câu tìm gần nhất (trong 24 giờ): bảng tin cộng điểm mạnh cho các bài này, xếp lên đầu danh sách
        events.stream().filter(Event::isSearch).filter(e -> e.at().isAfter(now.minusHours(SEARCH_RECENT_HOURS)))
                .max(Comparator.comparing(Event::at))
                .ifPresent(latest -> recommendations.addAll(recommendBySearch(basis, latest, already)));
        if (profile.isPresent()) {
            double[] xy = basis.pca().project(profile.get());
            userX = round(xy[0], 5);
            userY = round(xy[1], 5);
            vector = rounded(profile.get());
            Set<UUID> taken = recommendations.stream().map(Recommendation::id).collect(Collectors.toSet());
            recommendations.addAll(recommend(basis, profile.get(), already, taken));
        }

        return new InterestMap(basis.pca().mean().length,
                round(basis.pca().explained1(), 4), round(basis.pca().explained2(), 4),
                basis.posts(), topics, profile.isPresent(), events.size(), vectorized, UserInterestProfile.MIN_ITEMS,
                userX, userY, vector, interacted, searches, trail, recommendations, now);
    }

    /**
     * Tương tác 60 ngày gần nhất của người dùng (thích, bình luận, chia sẻ và tìm kiếm), sắp theo thời gian tăng dần, tối đa
     * {@link #MAX_EVENTS}.
     */
    List<Event> loadEvents(UUID userId, LocalDateTime now) {
        LocalDateTime since = now.minusDays(INTERACTION_WINDOW_DAYS);
        List<Event> events = new ArrayList<>();
        collect(events, reactionRepository.findUserInteractionRows(userId, since), "REACTION", ItemCollaborativeFilter.W_REACTION);
        collect(events, commentRepository.findUserInteractionRows(userId, since), "COMMENT", ItemCollaborativeFilter.W_COMMENT);
        collect(events, postRepository.findUserShareRows(userId, since), "SHARE", ItemCollaborativeFilter.W_SHARE);
        for (UserInterestProfile.Search s : postEmbeddingService.getSearchHistory(userId, since, UserInterestProfile.MAX_SEARCHES)) {
            if (s.vector().length > 0) events.add(new Event(null, SEARCH, UserInterestProfile.W_SEARCH, s.at(), s.query(), s.vector()));
        }
        events.sort(Comparator.comparing(Event::at));
        return events.size() > MAX_EVENTS ? new ArrayList<>(events.subList(events.size() - MAX_EVENTS, events.size())) : events;
    }

    private static void collect(List<Event> out, List<Object[]> rows, String action, double weight) {
        for (Object[] r : rows) {
            if (r[0] == null || r[1] == null) continue;
            out.add(new Event((UUID) r[0], action, weight, (LocalDateTime) r[1], null, null));
        }
    }

    /**
     * Vector sở thích dựng từ các tương tác trong {@code events} tính tại thời điểm {@code at}: đúng công thức bảng tin dùng
     * (UserInterestProfile), gồm bài đã tương tác và câu đã tìm kiếm.
     */
    private static Profile profileAt(UUID userId, List<Event> events, Map<UUID, float[]> embeddings, LocalDateTime at) {
        List<Interaction> posts = new ArrayList<>();
        List<UserInterestProfile.Search> searches = new ArrayList<>();
        for (Event e : events) {
            if (e.isSearch()) searches.add(new UserInterestProfile.Search(e.query(), e.vector(), e.at()));
            else posts.add(new Interaction(userId, e.postId(), e.weight(), e.at()));
        }
        List<UserInterestProfile.Weighted> items = UserInterestProfile.aggregate(userId, posts, at);
        List<UserInterestProfile.WeightedVector> searchWeights = UserInterestProfile.searchWeights(searches, at);
        return new Profile(UserInterestProfile.centroid(items, embeddings, searchWeights), items, searchWeights.size());
    }

    /** Các câu tìm kiếm (tối đa {@link UserInterestProfile#MAX_SEARCHES} câu gần nhất) chiếu lên bản đồ. */
    private static List<SearchPoint> searchPoints(MapBasis basis, List<Event> events, LocalDateTime now) {
        List<Event> searches = events.stream().filter(Event::isSearch)
                .sorted(Comparator.comparing(Event::at).reversed()).limit(UserInterestProfile.MAX_SEARCHES).toList();
        List<SearchPoint> out = new ArrayList<>();
        for (Event e : searches) {
            double[] xy = basis.pca().project(e.vector());
            double weight = UserInterestProfile.searchWeights(List.of(new UserInterestProfile.Search(e.query(), e.vector(), e.at())), now)
                    .stream().mapToDouble(UserInterestProfile.WeightedVector::weight).sum();
            out.add(new SearchPoint(e.query(), round(xy[0], 5), round(xy[1], 5), nearestTopic(basis, e.vector()), round(weight, 4), e.at()));
        }
        return out;
    }

    private List<InteractedPost> interactedPosts(MapBasis basis, List<UserInterestProfile.Weighted> items,
                                                 List<Event> events, Map<UUID, float[]> embeddings) {
        Map<UUID, List<Event>> byPost = events.stream().filter(e -> !e.isSearch())
                .collect(Collectors.groupingBy(Event::postId, LinkedHashMap::new, Collectors.toList()));
        Map<UUID, String> snippets = snippets(basis, items.stream().map(UserInterestProfile.Weighted::postId).toList());
        List<InteractedPost> out = new ArrayList<>();
        for (UserInterestProfile.Weighted w : items) {
            float[] v = embeddings.get(w.postId());
            if (v == null) continue;
            double[] xy = basis.pca().project(v);
            List<Event> evs = byPost.getOrDefault(w.postId(), List.of());
            Event strongest = evs.stream().max(Comparator.comparingDouble(Event::weight)).orElse(null);
            LocalDateTime last = evs.stream().map(Event::at).max(Comparator.naturalOrder()).orElse(null);
            out.add(new InteractedPost(w.postId(), round(xy[0], 5), round(xy[1], 5), nearestTopic(basis, v),
                    strongest == null ? "REACTION" : strongest.action(), evs.size(), round(w.weight(), 4),
                    snippets.getOrDefault(w.postId(), ""), last));
        }
        return out;
    }

    /**
     * Quỹ đạo: vị trí của vector sở thích ngay sau mỗi tương tác trong {@link #TRAIL_STEPS} tương tác gần nhất (chỉ tính các
     * tương tác đến thời điểm đó, suy giảm theo đúng thời điểm đó). Bước nào chưa đủ dữ liệu dựng hồ sơ thì bỏ qua.
     */
    private List<TrailPoint> trail(UUID userId, MapBasis basis, List<Event> events, Map<UUID, float[]> embeddings) {
        List<TrailPoint> out = new ArrayList<>();
        int from = Math.max(0, events.size() - TRAIL_STEPS);
        for (int j = from; j < events.size(); j++) {
            Event e = events.get(j);
            Optional<float[]> profile = profileAt(userId, events.subList(0, j + 1), embeddings, e.at()).vector();
            if (profile.isEmpty()) continue;
            double[] xy = basis.pca().project(profile.get());
            out.add(new TrailPoint(round(xy[0], 5), round(xy[1], 5), e.at(), e.action(), e.postId(), e.query()));
        }
        return out;
    }

    /** Bài chưa tương tác gần vector sở thích nhất (bảng tin cộng điểm theo nội dung khi cosine vượt sàn 0,45). */
    private List<Recommendation> recommend(MapBasis basis, float[] profile, Set<UUID> already, Set<UUID> taken) {
        return basis.unitVectors().entrySet().stream()
                .filter(e -> !already.contains(e.getKey()) && !taken.contains(e.getKey()))
                .map(e -> Map.entry(e.getKey(), dot(profile, e.getValue())))
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(RECOMMEND_LIMIT)
                .map(e -> recommendation(basis, e.getKey(), e.getValue(), UserInterestProfile.boost(e.getValue()), "INTEREST", null))
                .toList();
    }

    /** Bài chưa tương tác gần câu tìm gần nhất, chỉ lấy bài vượt ngưỡng bảng tin dùng để cộng điểm theo tìm kiếm. */
    private List<Recommendation> recommendBySearch(MapBasis basis, Event search, Set<UUID> already) {
        return basis.unitVectors().entrySet().stream()
                .filter(e -> !already.contains(e.getKey()))
                .map(e -> Map.entry(e.getKey(), dot(search.vector(), e.getValue())))
                .filter(e -> e.getValue() >= SEARCH_SIM_FLOOR)
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(SEARCH_RECOMMEND_LIMIT)
                .map(e -> recommendation(basis, e.getKey(), e.getValue(), 0, SEARCH, search.query()))
                .toList();
    }

    private static Recommendation recommendation(MapBasis basis, UUID id, double similarity, double boost, String source, String query) {
        PostPoint p = basis.posts().stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
        return new Recommendation(id, p == null ? "" : p.snippet(), round(similarity, 4), round(boost, 4),
                p == null ? -1 : p.topic(), source, query);
    }

    private Map<UUID, String> snippets(MapBasis basis, List<UUID> ids) {
        Map<UUID, String> out = new HashMap<>();
        Map<UUID, String> known = basis.posts().stream().collect(Collectors.toMap(PostPoint::id, PostPoint::snippet, (a, b) -> a));
        List<UUID> missing = new ArrayList<>();
        for (UUID id : ids) {
            if (known.containsKey(id)) out.put(id, known.get(id));
            else missing.add(id);
        }
        if (!missing.isEmpty()) {
            for (Object[] r : postRepository.findContentByIds(missing)) out.put((UUID) r[0], snippet((String) r[1], 100));
        }
        return out;
    }

    private static int nearestTopic(MapBasis basis, float[] v) {
        int best = -1;
        double bestSim = -2;
        for (TopicModel t : basis.topics()) {
            double s = dot(v, t.centroid());
            if (s > bestSim) {
                bestSim = s;
                best = t.id();
            }
        }
        return best;
    }

    /** Nền bản đồ (trục PCA, toạ độ bài, chủ đề) tính từ các bài công khai hiện có; null nếu chưa đủ bài có vector. */
    private MapBasis buildBasis(LocalDateTime now) {
        List<Object[]> rows = postRepository.findPublicPostSamplesSince(now.minusDays(SAMPLE_WINDOW_DAYS), PageRequest.of(0, SAMPLE_LIMIT));
        Map<UUID, float[]> vectors = postEmbeddingService.getEmbeddings(rows.stream().map(r -> (UUID) r[0]).toList());
        List<UUID> ids = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        List<Boolean> recent = new ArrayList<>();
        List<float[]> unit = new ArrayList<>();
        LocalDateTime recentSince = now.minusHours(RECENT_HOURS);
        for (Object[] r : rows) {
            float[] v = vectors.get((UUID) r[0]);
            if (v == null || v.length == 0) continue;
            ids.add((UUID) r[0]);
            contents.add((String) r[2]);
            recent.add(r[3] instanceof LocalDateTime at && at.isAfter(recentSince));
            unit.add(normalize(v));
        }
        if (unit.size() < 2) return null;

        float[][] matrix = unit.toArray(float[][]::new);
        PcaProjector.Basis pca = PcaProjector.fit(matrix);

        int[] assignment = new int[matrix.length];
        List<TopicModel> topics = new ArrayList<>();
        if (matrix.length >= MIN_TOPIC_POSTS) {
            // Đủ bài thì xét từ 3 chủ đề trở lên (2 cụm thường chỉ tách "bài kiểm thử" với phần còn lại); k chọn theo silhouette
            int kMin = matrix.length >= 30 ? 3 : 2;
            TopicClusterer.Clustering clustering = TopicClusterer.best(matrix, kMin, Math.min(MAX_TOPICS, matrix.length / 3), 42L);
            // Đánh số lại chủ đề theo số bài giảm dần để màu và thứ tự ổn định giữa các lần tính
            int[] size = new int[clustering.k()];
            for (int a : clustering.assignment()) size[a]++;
            List<Integer> order = new ArrayList<>();
            for (int c = 0; c < clustering.k(); c++) if (size[c] > 0) order.add(c);
            order.sort(Comparator.comparingInt((Integer c) -> size[c]).reversed());
            int[] newId = new int[clustering.k()];
            for (int i = 0; i < order.size(); i++) newId[order.get(i)] = i;
            for (int i = 0; i < matrix.length; i++) assignment[i] = newId[clustering.assignment()[i]];
            for (int i = 0; i < order.size(); i++) {
                int c = order.get(i);
                topics.add(topicModel(i, clustering.centroids()[c], matrix, contents, assignment, pca));
            }
        } else {
            java.util.Arrays.fill(assignment, -1);
        }

        List<PostPoint> points = new ArrayList<>(matrix.length);
        Map<UUID, float[]> unitById = new LinkedHashMap<>();
        for (int i = 0; i < matrix.length; i++) {
            double[] xy = pca.project(matrix[i]);
            points.add(new PostPoint(ids.get(i), round(xy[0], 5), round(xy[1], 5), assignment[i], snippet(contents.get(i), 100), recent.get(i)));
            unitById.put(ids.get(i), matrix[i]);
        }
        return new MapBasis(pca, points, unitById, topics);
    }

    private static TopicModel topicModel(int id, float[] centroid, float[][] matrix, List<String> contents, int[] assignment,
                                         PcaProjector.Basis pca) {
        Map<String, Integer> tagCount = new HashMap<>();
        int members = 0;
        int rep = -1;
        double repSim = -2;
        for (int i = 0; i < matrix.length; i++) {
            if (assignment[i] != id) continue;
            members++;
            for (String tag : TagAndHashtagHelper.extractHashtags(contents.get(i))) tagCount.merge(tag, 1, Integer::sum);
            double s = dot(matrix[i], centroid);
            if (s > repSim) {
                repSim = s;
                rep = i;
            }
        }
        // Chỉ lấy hashtag lặp lại trong cụm (≥ 2 bài): hashtag xuất hiện một lần không đại diện cho chủ đề
        List<String> hashtags = tagCount.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(3).map(Map.Entry::getKey).toList();
        String label = !hashtags.isEmpty() ? String.join(", ", hashtags.subList(0, Math.min(2, hashtags.size())))
                : rep >= 0 ? labelFrom(contents.get(rep)) : "Chủ đề " + (id + 1);
        double[] xy = pca.project(centroid);
        return new TopicModel(id, label, hashtags, members, normalize(centroid), round(xy[0], 5), round(xy[1], 5));
    }

    /**
     * Nhãn chủ đề từ bài đại diện: bỏ ký hiệu/emoji và phần trong ngoặc vuông ở đầu (vd "⏹ [ĐÃ KẾT THÚC]") và bỏ dấu "#" của
     * hashtag, tối đa 32 ký tự.
     */
    static String labelFrom(String content) {
        String t = content == null ? "" : content.replace("#", "").replaceAll("\\s+", " ").trim();
        t = t.replaceFirst("^(?:[^\\p{L}\\p{N}\\[]+|\\[[^\\]]*\\])+", "").trim();
        return t.isEmpty() ? snippet(content, 32) : snippet(t, 32);
    }

    static String snippet(String content, int max) {
        if (content == null) return "";
        String t = content.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max - 1).trim() + "…";
    }

    private static float[] normalize(float[] v) {
        double s = 0;
        for (float f : v) s += (double) f * f;
        double len = Math.sqrt(s);
        float[] out = new float[v.length];
        if (len == 0) return out;
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / len);
        return out;
    }

    private static double dot(float[] a, float[] b) {
        double na = 0, nb = 0, s = 0;
        for (int i = 0; i < a.length && i < b.length; i++) {
            s += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : s / Math.sqrt(na * nb);
    }

    private static float[] rounded(float[] v) {
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = Math.round(v[i] * 10000f) / 10000f;
        return out;
    }

    private static double round(double v, int digits) {
        double f = Math.pow(10, digits);
        return Math.round(v * f) / f;
    }
}
