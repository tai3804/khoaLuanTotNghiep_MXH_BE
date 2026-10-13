package iuh.fit.postservice.application.service.ranking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chấm điểm một bài viết cho bảng tin của một người xem. Hàm thuần, không truy cập DB để dễ kiểm thử.
 *
 * <h3>Chế độ RELEVANCE ("Tất cả")</h3>
 * <pre>
 * affinity   = bạn bè 3,0 | đang theo dõi 2,0 | bài của chính mình 0,5 (+1,5 trong 2 giờ đầu)
 *            + nhóm mình tham gia 1,5
 *            + min(3, 1,2 · ln(1 + số lần mình tương tác với tác giả trong 30 ngày qua))
 * engagement = ln(1 + thích + 2·bình luận + 3·chia sẻ)
 * freshness  = 0,5 ^ (tuổi bài / 24 giờ)                 // nửa đời 24 giờ
 * score      = (affinity + 0,6·engagement + 0,4) · (0,2 + 0,8·freshness) · chất lượng
 * </pre>
 * Nhờ vậy bài của bạn bè/người hay tương tác vẫn đáng xem sau vài ngày, nhưng bài ít thân thiết cần vừa mới vừa nhiều
 * tương tác mới chen lên được. Bài cũ gần như không bao giờ vượt bài mới có cùng mức thân thiết.
 *
 * <h3>Chế độ TRENDING ("Nổi bật")</h3>
 * Không dùng độ thân thiết, chỉ xét tốc độ tương tác, giống công thức "gravity" của Hacker News:
 * <pre>score = (thích + 2·bình luận + 3·chia sẻ) / (tuổi giờ + 2) ^ 1,3</pre>
 */
public final class PostRelevanceScorer {

    public enum Mode { RELEVANCE, TRENDING }

    static final double FRIEND = 3.0;
    static final double FOLLOWING = 2.0;
    static final double OWN = 0.5;
    /** Bài mình vừa đăng trong vòng 2 giờ được cộng thêm để luôn nằm gần đầu bảng tin của chính mình. */
    static final double OWN_FRESH_BONUS = 1.5;
    static final double OWN_FRESH_HOURS = 2.0;
    static final double MEMBER_GROUP = 1.5;
    static final double INTERACTION_CAP = 3.0;
    static final double FRESHNESS_HALF_LIFE_HOURS = 24.0;
    static final double TRENDING_GRAVITY = 1.3;

    /** Hệ số nhân điểm cho lần chọn thứ k của cùng một tác giả trong một trang kết quả (đa dạng hoá). */
    static final double AUTHOR_REPEAT_PENALTY = 0.75;
    static final double CONSECUTIVE_PENALTY = 0.5;

    private PostRelevanceScorer() {
    }

    public record Signals(
            double ageHours,
            long likes,
            long comments,
            long shares,
            boolean hasMedia,
            int contentLength,
            boolean own,
            boolean fromFriend,
            boolean fromFollowing,
            boolean fromMemberGroup,
            int interactionsWithAuthor30d,
            boolean pinned) {
    }

    public record Scored(double score, String reason) {
    }

    public static double engagement(long likes, long comments, long shares) {
        return Math.log(1.0 + Math.max(0, likes) + 2.0 * Math.max(0, comments) + 3.0 * Math.max(0, shares));
    }

    public static double freshness(double ageHours) {
        return Math.pow(0.5, Math.max(0, ageHours) / FRESHNESS_HALF_LIFE_HOURS);
    }

    public static Scored score(Mode mode, Signals s) {
        return mode == Mode.TRENDING ? trending(s) : relevance(s);
    }

    private static Scored relevance(Signals s) {
        double affinity = 0;
        String reason = "recent";
        double strongest = 0;

        if (s.fromFriend()) { affinity += FRIEND; if (FRIEND > strongest) { strongest = FRIEND; reason = "friend"; } }
        else if (s.fromFollowing()) { affinity += FOLLOWING; if (FOLLOWING > strongest) { strongest = FOLLOWING; reason = "following"; } }
        else if (s.own()) {
            affinity += OWN + (s.ageHours() < OWN_FRESH_HOURS ? OWN_FRESH_BONUS : 0.0);
            reason = "own";
            strongest = OWN;
        }

        if (s.fromMemberGroup()) { affinity += MEMBER_GROUP; if (MEMBER_GROUP > strongest) { strongest = MEMBER_GROUP; reason = "group"; } }

        double interaction = Math.min(INTERACTION_CAP, 1.2 * Math.log(1.0 + Math.max(0, s.interactionsWithAuthor30d())));
        affinity += interaction;
        if (interaction > strongest) { strongest = interaction; reason = "interacted"; }

        double engagement = engagement(s.likes(), s.comments(), s.shares());
        if (affinity < 1.0 && engagement >= 2.0) reason = "popular";

        double quality = 1.0 + (s.hasMedia() ? 0.15 : 0.0) + (s.contentLength() > 200 ? 0.10 : 0.0);
        double base = affinity + 0.6 * engagement + 0.4;
        double score = base * (0.2 + 0.8 * freshness(s.ageHours())) * quality;
        if (s.pinned()) score *= 1.1;
        return new Scored(score, reason);
    }

    private static Scored trending(Signals s) {
        double interactions = Math.max(0, s.likes()) + 2.0 * Math.max(0, s.comments()) + 3.0 * Math.max(0, s.shares());
        double score = interactions / Math.pow(Math.max(0, s.ageHours()) + 2.0, TRENDING_GRAVITY);
        return new Scored(score, "trending");
    }

    /** Một mục đã chấm điểm, kèm tác giả để đa dạng hoá. */
    public record Item(UUID postId, UUID authorId, double score, String reason) {
    }

    /**
     * Chọn tối đa {@code limit} mục theo thứ tự điểm, nhưng phạt những bài của cùng một tác giả đã được chọn
     * (mỗi bài thêm nhân 0,75) và phạt mạnh hơn nếu ba bài liên tiếp cùng một người. Tránh tình trạng một người
     * đăng dồn dập chiếm cả bảng tin.
     */
    public static List<Item> diversify(List<Item> items, int limit) {
        List<Item> pool = new ArrayList<>(items);
        List<Item> picked = new ArrayList<>(Math.min(limit, pool.size()));
        Map<UUID, Integer> perAuthor = new HashMap<>();

        while (!pool.isEmpty() && picked.size() < limit) {
            int bestIndex = -1;
            double bestAdjusted = -1;
            UUID last1 = picked.size() >= 1 ? picked.get(picked.size() - 1).authorId() : null;
            UUID last2 = picked.size() >= 2 ? picked.get(picked.size() - 2).authorId() : null;

            for (int i = 0; i < pool.size(); i++) {
                Item it = pool.get(i);
                double adjusted = it.score() * Math.pow(AUTHOR_REPEAT_PENALTY, perAuthor.getOrDefault(it.authorId(), 0));
                if (it.authorId() != null && it.authorId().equals(last1) && it.authorId().equals(last2)) {
                    adjusted *= CONSECUTIVE_PENALTY;
                }
                if (adjusted > bestAdjusted) {
                    bestAdjusted = adjusted;
                    bestIndex = i;
                }
            }
            Item chosen = pool.remove(bestIndex);
            picked.add(chosen);
            perAuthor.merge(chosen.authorId(), 1, Integer::sum);
        }
        return picked;
    }
}
