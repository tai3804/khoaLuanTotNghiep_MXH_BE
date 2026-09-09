package iuh.fit.userservice.presentation.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserConnectionStatusResponse {
    boolean isFriend;
    boolean isFollowing;
    boolean isFollowedBy;
    boolean hasPendingSent;
    boolean hasPendingReceived;
    UUID pendingRequestId;
}
