package iuh.fit.userservice.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReputationHistoryResponse {
    private UUID id;
    /** PENALTY | REFUND */
    private String type;
    private String action;
    private String sourceType;
    private UUID sourceId;
    private int points;
    private int scoreBefore;
    private int scoreAfter;
    private String reason;
    private boolean reverted;
    private LocalDateTime createdAt;
}
