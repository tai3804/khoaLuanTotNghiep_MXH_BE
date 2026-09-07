package iuh.fit.chatservice.application.features.conversation.commands.update_group_info;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateGroupInfoCommand {
    UUID conversationId;
    UUID currentUserId;
    String name;
    String avatarUrl;
}
