package iuh.fit.userservice.application.features.user_block.queries.check_user_block;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CheckUserBlockQueryResult {
    UUID userId;
    UUID targetId;
    boolean isBlockedByMe;
    boolean isBlockedByTarget;
    boolean isBlocked; // Any direction block
}
