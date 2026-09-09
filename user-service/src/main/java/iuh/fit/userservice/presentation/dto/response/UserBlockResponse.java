package iuh.fit.userservice.presentation.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserBlockResponse {
    UUID id;
    UUID blockerId;
    UUID blockedId;
    String blockedFirstName;
    String blockedLastName;
    String blockedAvatarUrl;
    String reason;
    LocalDateTime createdAt;
}
