package iuh.fit.userservice.presentation.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserPresenceResponse {
    UUID userId;
    boolean isOnline;
    Instant lastActiveAt;
}
