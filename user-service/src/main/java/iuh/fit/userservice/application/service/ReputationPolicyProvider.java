package iuh.fit.userservice.application.service;

import iuh.fit.userservice.domain.enums.ReputationLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Bảng quyền hạn theo bậc uy tín (mức phạt của từng bậc):
 * <pre>
 * Bậc        | điểm   | đăng bài                        | bình luận                       | live / tạo nhóm | độ ưu tiên bảng tin
 * Rất tốt    | 90-100 | không hạn chế                   | không hạn chế                   | được            | 100%
 * Tốt        | 80-89  | không hạn chế                   | không hạn chế                   | được            | 100%
 * Cần chú ý  | 50-79  | cách 1 phút, tối đa 30 bài/ngày | cách 10 giây, tối đa 300/ngày   | được            | 85%
 * Yếu        | 25-49  | cách 10 phút, tối đa 5 bài/ngày | cách 30 giây, tối đa 50/ngày    | không           | 50%
 * Rất yếu    | 10-24  | không được đăng                 | cách 2 phút, tối đa 10/ngày     | không           | 20%
 * Bị khóa    | 0-9    | khóa tài khoản (mặc định 30 ngày)
 * </pre>
 */
@Component
public class ReputationPolicyProvider {

    @Value("${reputation.initial-score:100}")
    private int maxScore;
    @Value("${reputation.lock.days:30}")
    private int lockDays;

    public ReputationPolicy of(ReputationLevel level) {
        int min = level.getMinScore();
        int max = level == ReputationLevel.EXCELLENT ? maxScore : upperBoundOf(level);
        String name = level.name();
        String label = level.getLabel();
        return switch (level) {
            case EXCELLENT, GOOD -> new ReputationPolicy(name, label, min, max,
                    0, 0, 0, 0, true, true, true, true, 1.0, false, List.of());
            case CAUTION -> new ReputationPolicy(name, label, min, max,
                    60, 30, 10, 300, true, true, true, true, 0.85, false,
                    restrictions(60, 30, 10, 300, true, true, true, 0.85));
            case WEAK -> new ReputationPolicy(name, label, min, max,
                    600, 5, 30, 50, true, true, false, false, 0.5, false,
                    restrictions(600, 5, 30, 50, true, false, false, 0.5));
            case VERY_WEAK -> {
                List<String> lines = restrictions(0, 0, 120, 10, false, false, false, 0.2);
                lines.add("Dưới " + ReputationLevel.VERY_WEAK.getMinScore() + " điểm: tài khoản bị khóa " + lockDays + " ngày");
                yield new ReputationPolicy(name, label, min, max, 0, 0, 120, 10, false, true, false, false, 0.2, false, lines);
            }
            case LOCKED -> new ReputationPolicy(name, label, min, max,
                    0, 0, 0, 0, false, false, false, false, 0.0, true,
                    List.of("Tài khoản bị khóa " + lockDays + " ngày, không thể đăng nhập"));
        };
    }

    public ReputationPolicy of(int score) {
        return of(ReputationLevel.of(score));
    }

    /** Tất cả các bậc từ cao xuống thấp (hiển thị bảng quyền hạn cho người dùng). */
    public List<ReputationPolicy> all() {
        return Arrays.stream(ReputationLevel.values()).map(this::of).toList();
    }

    private int upperBoundOf(ReputationLevel level) {
        ReputationLevel[] levels = ReputationLevel.values();
        for (int i = 1; i < levels.length; i++) {
            if (levels[i] == level) {
                return levels[i - 1].getMinScore() - 1;
            }
        }
        return maxScore;
    }

    private static List<String> restrictions(int postInterval, int postsPerDay, int commentInterval, int commentsPerDay,
                                             boolean canPost, boolean canGoLive, boolean canCreateGroup, double feedWeight) {
        List<String> lines = new ArrayList<>();
        if (!canPost) {
            lines.add("Không được đăng bài, chia sẻ hay phát trực tiếp");
        } else {
            lines.add("Đăng bài cách nhau tối thiểu " + duration(postInterval) + ", tối đa " + postsPerDay + " bài/ngày");
        }
        lines.add("Bình luận cách nhau tối thiểu " + duration(commentInterval) + ", tối đa " + commentsPerDay + " bình luận/ngày");
        if (canPost && !canGoLive) {
            lines.add("Không được phát trực tiếp" + (canCreateGroup ? "" : " và tạo nhóm"));
        } else if (!canPost && !canCreateGroup) {
            lines.add("Không được tạo nhóm");
        }
        if (feedWeight < 1.0) {
            lines.add("Bài viết ít được đề xuất trên bảng tin hơn (giảm " + Math.round((1 - feedWeight) * 100) + "%)");
        }
        return lines;
    }

    private static String duration(int seconds) {
        if (seconds >= 60 && seconds % 60 == 0) {
            return (seconds / 60) + " phút";
        }
        return seconds + " giây";
    }
}
