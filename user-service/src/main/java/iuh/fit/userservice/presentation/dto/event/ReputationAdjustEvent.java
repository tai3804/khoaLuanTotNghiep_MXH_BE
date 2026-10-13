package iuh.fit.userservice.presentation.dto.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Sự kiện "user.reputation.adjust" do post-service phát khi một quyết định kiểm duyệt ảnh hưởng tới điểm uy tín. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReputationAdjustEvent {
    /** PENALTY | REVERT */
    private String type;
    private UUID userId;
    /** WARN_USER | AUTO_HIDE | HIDE_POST | DELETE_POST (chỉ với PENALTY) */
    private String action;
    private String sourceType;
    private UUID sourceId;
    private String reason;
    private UUID actorId;
}
