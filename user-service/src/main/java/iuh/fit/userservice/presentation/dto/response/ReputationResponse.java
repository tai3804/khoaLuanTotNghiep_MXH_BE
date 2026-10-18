package iuh.fit.userservice.presentation.dto.response;

import iuh.fit.userservice.application.service.ReputationPolicy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReputationResponse {
    private UUID userId;
    private int score;
    private int maxScore;
    /** EXCELLENT | GOOD | CAUTION | WEAK | VERY_WEAK | LOCKED */
    private String level;
    private String levelLabel;
    /** Điểm tối thiểu của bậc kế tiếp phía trên (null nếu đã ở bậc cao nhất). */
    private Integer nextLevelScore;
    /** Quyền hạn / mức phạt của bậc hiện tại. */
    private ReputationPolicy policy;
    /** Bảng quyền hạn của tất cả các bậc, từ cao xuống thấp. */
    private List<ReputationPolicy> tiers;
    /** Số điểm tự hồi phục mỗi ngày không bị trừ điểm. */
    private int recoveryPerDay;
    /** Thời điểm được cộng điểm hồi phục kế tiếp (null nếu đã đạt điểm tối đa hoặc đang bị khóa). */
    private LocalDateTime nextRecoveryAt;
    /** Tài khoản bị khóa tới thời điểm này (null nếu không bị khóa). */
    private LocalDateTime lockedUntil;
}
