package iuh.fit.userservice.application.features.user_presence.queries.get_user_presence;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserPresenceResult {
    UUID userId;
    boolean isOnline;
    Instant lastActiveAt;
}
