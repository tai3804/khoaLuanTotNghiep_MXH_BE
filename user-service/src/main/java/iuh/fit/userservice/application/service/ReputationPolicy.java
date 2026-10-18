package iuh.fit.userservice.application.service;

import java.util.List;

/**
 * Quyền hạn và mức hạn chế của một bậc uy tín. Giá trị 0 ở các giới hạn nghĩa là không giới hạn.
 * user-service là nơi duy nhất quyết định chính sách này; các service khác (post-service) lấy qua API nội bộ.
 */
public record ReputationPolicy(
        String level,
        String levelLabel,
        int minScore,
        int maxScore,
        int minPostIntervalSeconds,
        int maxPostsPerDay,
        int minCommentIntervalSeconds,
        int maxCommentsPerDay,
        boolean canPost,
        boolean canComment,
        boolean canGoLive,
        boolean canCreateGroup,
        /** Hệ số nhân điểm xếp hạng bài viết của người này trên bảng tin (1 = bình thường). */
        double feedWeight,
        boolean accountLocked,
        /** Mô tả các hạn chế bằng tiếng Việt để hiển thị cho người dùng (rỗng = không bị hạn chế). */
        List<String> restrictions) {
}
