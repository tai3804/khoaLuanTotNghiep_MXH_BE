package iuh.fit.postservice.application.service.topic;

import iuh.fit.postservice.application.service.PostEmbeddingService;
import iuh.fit.postservice.application.util.TagAndHashtagHelper;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.RequiredArgsConstructor;
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
 * Phát hiện <b>chủ đề</b> đang nổi trong cộng đồng (không chỉ hashtag): gom cụm các bài công khai gần đây theo embedding rồi đo
 * mức tăng của từng cụm so với cửa sổ liền trước cùng độ dài.
 *
 * <pre>
 * 1. Lấy bài công khai trong cửa sổ hiện tại [now − d, now] và cửa sổ trước [now − 2d, now − d], có vector ngữ nghĩa.
 * 2. Gom cụm cửa sổ hiện tại bằng k-means cầu (k-means++, k chọn theo silhouette), xem {@link TopicClusterer}.
 * 3. Mỗi bài của cửa sổ trước được gán vào cụm có tâm gần nhất nếu đủ gần (không xa tâm hơn thành viên xa nhất của cụm quá
 *    0,05) → đếm p cho cụm.
 * 4. Với cụm có c bài hiện tại và p bài trước đó:
 *      tăng trưởng (%) = (c − p) / (p + 1) · 100
 *      điểm bùng nổ    = (c − (p + 1)) / √(p + 1)          (z-score xấp xỉ Poisson, giống hashtag)
 * 5. Nhãn cụm: hashtag phổ biến nhất trong cụm và bài đại diện (gần tâm nhất).
 * </pre>
 *
 * Hashtag chỉ bắt được chủ đề có người gắn thẻ; cụm theo embedding bắt cả các bài cùng chủ đề nhưng dùng từ khác nhau.
 */
@Service
@RequiredArgsConstructor
public class TrendingTopicService {

    static final int SAMPLE_LIMIT = 500;
    static final int MIN_POSTS = 6;
    static final int MAX_K = 8;
    static final double VIRAL_BURST = 2.0;
    static final int VIRAL_MIN_COUNT = 3;
    static final double MEMBERSHIP_TOLERANCE = 0.05;

    private final PostRepository postRepository;
    private final PostEmbeddingService postEmbeddingService;

    /** Một bài trong mẫu: nội dung, thời điểm, tương tác và vector. */
    public record Sample(UUID id, UUID authorId, String content, LocalDateTime createdAt, long interactions, float[] vector) {
    }

    public record Topic(int id, int posts, int previousPosts, double growthPercent, double burstScore, boolean trending,
                        long engagement, int authors, double cohesion, List<String> hashtags,
                        UUID representativePostId, String representativeSnippet) {
    }

    public record Report(int days, int totalPosts, int k, double silhouette, List<Topic> topics) {
    }

    public Report analyze(int days) {
        int d = Math.max(1, Math.min(days, 60));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime curStart = now.minusDays(d);

        List<Object[]> rows = postRepository.findPublicPostSamplesSince(now.minusDays(2L * d), PageRequest.of(0, SAMPLE_LIMIT * 2));
        Map<UUID, float[]> vectors = postEmbeddingService.getEmbeddings(
                rows.stream().map(r -> (UUID) r[0]).toList());

        List<Sample> current = new ArrayList<>();
        List<Sample> previous = new ArrayList<>();
        for (Object[] r : rows) {
            UUID id = (UUID) r[0];
            float[] v = vectors.get(id);
            if (v == null) continue;
            LocalDateTime createdAt = (LocalDateTime) r[3];
            long interactions = ((Number) r[4]).longValue() + 2 * ((Number) r[5]).longValue() + 3 * ((Number) r[6]).longValue();
            Sample s = new Sample(id, (UUID) r[1], (String) r[2], createdAt, interactions, v);
            if (createdAt.isAfter(curStart)) {
                if (current.size() < SAMPLE_LIMIT) current.add(s);
            } else {
                previous.add(s);
            }
        }
        return analyze(d, current, previous);
    }

    /** Phần thuần của phân tích, kiểm thử được mà không cần cơ sở dữ liệu. */
    static Report analyze(int days, List<Sample> current, List<Sample> previous) {
        if (current.size() < MIN_POSTS) return new Report(days, current.size(), 0, 0, List.of());

        float[][] vectors = current.stream().map(Sample::vector).toArray(float[][]::new);
        TopicClusterer.Clustering clustering = TopicClusterer.best(vectors, 2, Math.min(MAX_K, current.size() / 3), 42L);
        int k = clustering.k();
        float[][] unit = new float[vectors.length][];
        for (int i = 0; i < vectors.length; i++) unit[i] = TopicClusterer.normalize(vectors[i]);

        // Ngưỡng "đủ gần tâm" của từng cụm: một bài của cửa sổ trước thuộc chủ đề nếu nó gần tâm ít nhất bằng thành viên xa nhất
        // của cửa sổ hiện tại (trừ dung sai nhỏ). Ước lượng bằng trung bình ± độ lệch chuẩn không ổn định với cụm chỉ vài bài.
        double[] sum = new double[k];
        double[] minMember = new double[k];
        int[] size = new int[k];
        java.util.Arrays.fill(minMember, 2.0);
        for (int i = 0; i < unit.length; i++) {
            int c = clustering.assignment()[i];
            double s = TopicClusterer.dot(unit[i], clustering.centroids()[c]);
            sum[c] += s;
            minMember[c] = Math.min(minMember[c], s);
            size[c]++;
        }
        double[] cohesion = new double[k];
        double[] minSim = new double[k];
        for (int c = 0; c < k; c++) {
            if (size[c] == 0) continue;
            cohesion[c] = sum[c] / size[c];
            minSim[c] = minMember[c] - MEMBERSHIP_TOLERANCE;
        }

        int[] previousCount = new int[k];
        for (Sample p : previous) {
            float[] v = TopicClusterer.normalize(p.vector());
            int bestC = -1;
            double bestSim = -2;
            for (int c = 0; c < k; c++) {
                double s = TopicClusterer.dot(v, clustering.centroids()[c]);
                if (s > bestSim) {
                    bestSim = s;
                    bestC = c;
                }
            }
            if (bestC >= 0 && size[bestC] > 0 && bestSim >= minSim[bestC]) previousCount[bestC]++;
        }

        List<Topic> topics = new ArrayList<>();
        for (int c = 0; c < k; c++) {
            if (size[c] == 0) continue;
            List<Integer> members = new ArrayList<>();
            for (int i = 0; i < unit.length; i++) if (clustering.assignment()[i] == c) members.add(i);

            Map<String, Integer> tagCount = new HashMap<>();
            Set<UUID> authors = new HashSet<>();
            long engagement = 0;
            int repIndex = members.get(0);
            double repSim = -2;
            for (int i : members) {
                Sample s = current.get(i);
                authors.add(s.authorId());
                engagement += Math.max(0, s.interactions());
                for (String tag : TagAndHashtagHelper.extractHashtags(s.content())) tagCount.merge(tag, 1, Integer::sum);
                double sim = TopicClusterer.dot(unit[i], clustering.centroids()[c]);
                if (sim > repSim) {
                    repSim = sim;
                    repIndex = i;
                }
            }
            List<String> hashtags = tagCount.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                    .limit(3).map(e -> "#" + e.getKey()).collect(Collectors.toList());

            int cur = size[c];
            int prev = previousCount[c];
            double growth = (cur - prev) * 100.0 / (prev + 1);
            double burst = (cur - (prev + 1)) / Math.sqrt(prev + 1.0);
            Sample rep = current.get(repIndex);
            topics.add(new Topic(c, cur, prev, round1(growth), round2(burst),
                    burst >= VIRAL_BURST && cur >= VIRAL_MIN_COUNT, engagement, authors.size(), round3(cohesion[c]),
                    hashtags, rep.id(), snippet(rep.content())));
        }
        topics.sort(Comparator.comparingDouble(Topic::burstScore).reversed()
                .thenComparing(Comparator.comparingInt(Topic::posts).reversed()));
        return new Report(days, current.size(), k, round3(clustering.silhouette()), topics);
    }

    private static String snippet(String content) {
        if (content == null) return "";
        String t = content.replaceAll("\\s+", " ").trim();
        return t.length() <= 120 ? t : t.substring(0, 117) + "...";
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
