package iuh.fit.userservice.application.features.user_block.commands.block_user;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BlockUserCommand {
    UUID blockerId;
    UUID targetId;
    String reason;
}
