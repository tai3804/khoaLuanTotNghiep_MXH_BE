package iuh.fit.userservice.application.features.user_block.queries.get_blocked_users;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserBlockResult {
    UUID id;
    UUID blockerId;
    UUID blockedId;
    String blockedFirstName;
    String blockedLastName;
    String blockedAvatarUrl;
    String reason;
    LocalDateTime createdAt;
}
