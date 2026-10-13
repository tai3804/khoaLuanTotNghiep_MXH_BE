package iuh.fit.userservice.application.service.suggestion;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Chấm điểm một người có phù hợp để gợi ý kết bạn hay không. Hàm thuần (không truy cập DB) để dễ kiểm thử.
 *
 * Nguyên tắc: tín hiệu XÃ HỘI thật (bạn chung, cùng nhóm, đang theo dõi nhau) luôn xếp trên tín hiệu chỉ dựa vào hồ sơ
 * (cùng địa điểm, độ tuổi, hoạt động gần đây). Mức điểm của người chỉ có tín hiệu hồ sơ tối đa khoảng 4,
 * còn người có ít nhất một tín hiệu xã hội luôn có điểm cơ sở từ 5 trở lên.
 *
 * Bạn chung được tính theo Adamic–Adar: mỗi bạn chung đóng góp 1 / ln(số bạn của người đó + 2),
 * nên một người bạn "quen tất cả mọi người" ít giá trị hơn một người bạn có vòng quan hệ nhỏ, thân thiết.
 */
public final class FriendSuggestionScorer {

    // Điểm cơ sở khi có tín hiệu xã hội
    static final double MUTUAL_BASE = 6.0;
    static final double MUTUAL_WEIGHT = 8.0;
    static final double MUTUAL_CAP = 26.0;
    static final double GROUP_BASE = 5.0;
    static final double GROUP_CAP = 5.0;
    static final double FOLLOWS_ME = 7.0;
    static final double I_FOLLOW = 4.0;

    // Tín hiệu hồ sơ (cố ý nhỏ)
    static final double SAME_LOCATION = 1.0;
    static final double ONLINE_NOW = 0.6;
    static final double ACTIVE_7D = 0.5;
    static final double ACTIVE_30D = 0.2;
    static final double STALE_PENALTY = -2.0;
    static final double NEW_MEMBER = 0.6;
    static final double POPULARITY_CAP = 0.6;
    static final double JITTER_MAX = 0.3;

    private FriendSuggestionScorer() {
    }

    /** Dữ liệu quan hệ và hồ sơ của một ứng viên so với người đang xem. */
    public record Signals(
            List<Integer> mutualFriendDegrees,
            int commonGroups,
            double commonGroupWeight,
            boolean followsMe,
            boolean iFollow,
            String viewerLocation,
            String candidateLocation,
            Integer ageGapYears,
            boolean online,
            Instant lastActiveAt,
            LocalDateTime createdAt,
            long friendCount) {
    }

    public record Scored(double score, long mutualCount, List<String> reasons, boolean hasSocialSignal) {
    }

    /** Trọng số của một nhóm chung: nhóm nhỏ, thân mật có ý nghĩa hơn nhóm có hàng nghìn thành viên. */
    public static double groupWeight(int memberCount) {
        if (memberCount <= 30) return 2.0;
        if (memberCount <= 100) return 1.5;
        if (memberCount <= 300) return 1.0;
        return 0.5;
    }

    /** Tổng Adamic–Adar của các bạn chung. */
    public static double adamicAdar(List<Integer> mutualFriendDegrees) {
        double sum = 0;
        if (mutualFriendDegrees != null) {
            for (int degree : mutualFriendDegrees) {
                sum += 1.0 / Math.log(Math.max(degree, 0) + 2.0);
            }
        }
        return sum;
    }

    public static Scored score(UUID viewerId, UUID candidateId, Signals s, LocalDateTime now) {
        double score = 0;
        List<String> reasons = new ArrayList<>();

        long mutualCount = s.mutualFriendDegrees() == null ? 0 : s.mutualFriendDegrees().size();
        boolean social = false;

        if (mutualCount > 0) {
            social = true;
            score += MUTUAL_BASE + Math.min(MUTUAL_CAP, MUTUAL_WEIGHT * adamicAdar(s.mutualFriendDegrees()));
            reasons.add(mutualCount + " bạn chung");
        }
        if (s.commonGroups() > 0) {
            social = true;
            score += GROUP_BASE + Math.min(GROUP_CAP, s.commonGroupWeight());
            reasons.add("Cùng " + s.commonGroups() + " nhóm");
        }
        if (s.followsMe()) {
            social = true;
            score += FOLLOWS_ME;
            reasons.add("Đang theo dõi bạn");
        } else if (s.iFollow()) {
            social = true;
            score += I_FOLLOW;
            reasons.add("Bạn đang theo dõi");
        }

        if (hasText(s.viewerLocation()) && hasText(s.candidateLocation())
                && s.viewerLocation().trim().equalsIgnoreCase(s.candidateLocation().trim())) {
            score += SAME_LOCATION;
            reasons.add("Cùng sống tại " + s.candidateLocation().trim());
        }
        if (s.ageGapYears() != null) {
            if (s.ageGapYears() <= 2) score += 0.6;
            else if (s.ageGapYears() <= 5) score += 0.3;
        }

        Instant active = s.lastActiveAt();
        if (s.online()) {
            score += ONLINE_NOW;
            reasons.add("Đang hoạt động");
        } else if (active != null) {
            long days = Duration.between(active, now.atZone(java.time.ZoneId.systemDefault()).toInstant()).toDays();
            if (days <= 7) score += ACTIVE_7D;
            else if (days <= 30) score += ACTIVE_30D;
            else if (days > 90) score += STALE_PENALTY;
        }

        if (s.createdAt() != null && Duration.between(s.createdAt(), now).toDays() <= 14) {
            score += NEW_MEMBER;
            reasons.add("Thành viên mới");
        }

        score += Math.min(POPULARITY_CAP, 0.15 * Math.log(1 + Math.max(0, s.friendCount())));

        // Nhiễu xác định theo (người xem, ứng viên, ngày): hai người điểm bằng nhau sẽ luân phiên thứ tự mỗi ngày
        // thay vì luôn hiện cùng một người ở trên.
        long epochDay = now.toLocalDate().toEpochDay();
        score += Math.floorMod(Objects.hash(viewerId, candidateId, epochDay), 1000) / 1000.0 * JITTER_MAX;

        return new Scored(score, mutualCount, reasons, social);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
