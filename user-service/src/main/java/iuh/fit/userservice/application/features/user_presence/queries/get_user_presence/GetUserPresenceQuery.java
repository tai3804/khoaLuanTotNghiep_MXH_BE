package iuh.fit.userservice.application.features.user_presence.queries.get_user_presence;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetUserPresenceQuery {
    UUID targetUserId;
    List<UUID> targetUserIds;
}
