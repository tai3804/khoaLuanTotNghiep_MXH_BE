package iuh.fit.userservice.application.features.user_block.commands.unblock_user;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UnblockUserCommand {
    UUID blockerId;
    UUID targetId;
}
