package iuh.fit.userservice.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReputationResponse {
    private UUID userId;
    private int score;
    private int maxScore;
    /** EXCELLENT | GOOD | CAUTION | WARNING | CRITICAL */
    private String level;
    private String levelLabel;
    /** Điểm tối thiểu của bậc kế tiếp phía trên (null nếu đã ở bậc cao nhất). */
    private Integer nextLevelScore;
}
