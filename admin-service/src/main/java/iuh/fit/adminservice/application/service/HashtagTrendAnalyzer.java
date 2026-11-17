package iuh.fit.adminservice.application.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phát hiện hashtag đang nổi bằng cách so hai cửa sổ thời gian liền kề có cùng độ dài.
 *
 * <h3>Số liệu</h3>
 * Với mỗi hashtag, đếm số bài chứa nó (mỗi bài một lần) trong cửa sổ hiện tại {@code c} và cửa sổ liền trước {@code p}.
 * <pre>
 * tăng trưởng (%) = (c − p) / (p + 1) · 100        // làm mượt Laplace, tránh chia cho 0 khi hashtag mới xuất hiện
 * điểm bùng nổ    = (c − (p + 1)) / √(p + 1)        // z-score xấp xỉ Poisson: kỳ vọng là p + 1, độ lệch chuẩn √(p + 1)
 * </pre>
 * Hashtag xếp theo điểm bùng nổ giảm dần rồi đến số bài; chỉ giữ hashtag có c ≥ 1 và c ≥ p (không giảm).
 * Trạng thái VIRAL khi điểm bùng nổ ≥ 3 và c ≥ 3.
 *
 * <h3>Cửa sổ thích ứng</h3>
 * Hệ thống ít bài thì cửa sổ 24 giờ có thể rỗng, nên thử lần lượt 24 giờ, 72 giờ, 168 giờ và dừng ở cửa sổ đầu tiên
 * có ít nhất {@link #MIN_OCCURRENCES} lượt hashtag; cửa sổ liền trước luôn có cùng độ dài.
 */
public final class HashtagTrendAnalyzer {

    public static final int[] WINDOW_HOURS = {24, 72, 168};
    public static final int MIN_OCCURRENCES = 5;
    public static final double VIRAL_SCORE = 3.0;
    public static final int VIRAL_MIN_COUNT = 3;

    private static final Pattern HASHTAG = Pattern.compile("#[\\p{L}\\p{N}_]+");

    private HashtagTrendAnalyzer() {
    }

    /** Một bài viết: nội dung, thời điểm đăng và tổng điểm tương tác (thích + 2·bình luận + 3·chia sẻ). */
    public record PostSample(String content, LocalDateTime createdAt, long interactions) {
    }

    public record Trend(String tag, long currentCount, long previousCount, double growthPercent,
                        double burstScore, long engagement, boolean viral, int windowHours) {
    }

    public static List<Trend> analyze(List<PostSample> posts, LocalDateTime now, int limit) {
        List<Trend> best = List.of();
        for (int hours : WINDOW_HOURS) {
            best = analyzeWindow(posts, now, hours, limit);
            long occurrences = best.stream().mapToLong(Trend::currentCount).sum();
            if (occurrences >= MIN_OCCURRENCES) break;
        }
        return best;
    }

    static List<Trend> analyzeWindow(List<PostSample> posts, LocalDateTime now, int windowHours, int limit) {
        LocalDateTime curStart = now.minusHours(windowHours);
        LocalDateTime prevStart = now.minusHours(2L * windowHours);

        Map<String, long[]> counts = new HashMap<>();  // {c, p, engagement trong cửa sổ hiện tại}
        Map<String, String> display = new HashMap<>();
        for (PostSample post : posts) {
            if (post.content() == null || post.createdAt() == null) continue;
            boolean inCur = post.createdAt().isAfter(curStart) && !post.createdAt().isAfter(now);
            boolean inPrev = !inCur && post.createdAt().isAfter(prevStart) && !post.createdAt().isAfter(curStart);
            if (!inCur && !inPrev) continue;
            for (String tag : distinctTags(post.content())) {
                String key = tag.toLowerCase(Locale.ROOT);
                display.putIfAbsent(key, tag);
                long[] cell = counts.computeIfAbsent(key, k -> new long[3]);
                if (inCur) {
                    cell[0]++;
                    cell[2] += Math.max(0, post.interactions());
                } else {
                    cell[1]++;
                }
            }
        }

        List<Trend> trends = new ArrayList<>();
        for (Map.Entry<String, long[]> e : counts.entrySet()) {
            long c = e.getValue()[0];
            long p = e.getValue()[1];
            if (c < 1 || c < p) continue;
            double growth = (c - p) * 100.0 / (p + 1);
            double burst = (c - (p + 1)) / Math.sqrt(p + 1.0);
            boolean viral = burst >= VIRAL_SCORE && c >= VIRAL_MIN_COUNT;
            trends.add(new Trend(display.get(e.getKey()), c, p, round1(growth), round2(burst),
                    e.getValue()[2], viral, windowHours));
        }
        trends.sort(Comparator.comparingDouble(Trend::burstScore).reversed()
                .thenComparing(Comparator.comparingLong(Trend::currentCount).reversed())
                .thenComparing(Trend::tag));
        return trends.size() > limit ? new ArrayList<>(trends.subList(0, limit)) : trends;
    }

    /** Các hashtag khác nhau trong một bài, không phân biệt hoa thường (giữ dạng viết xuất hiện đầu tiên). */
    static Set<String> distinctTags(String content) {
        Map<String, String> byKey = new LinkedHashMap<>();
        Matcher m = HASHTAG.matcher(content);
        while (m.find()) {
            byKey.putIfAbsent(m.group().toLowerCase(Locale.ROOT), m.group());
        }
        return new LinkedHashSet<>(byKey.values());
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
