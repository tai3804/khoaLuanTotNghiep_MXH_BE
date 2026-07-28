package iuh.fit.userservice.application.features.user_connection.commands.unfollow_user;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UnfollowUserCommand {
    UUID followerId;
    UUID targetId;
}
