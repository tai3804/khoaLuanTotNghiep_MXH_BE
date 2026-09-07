package iuh.fit.chatservice.application.features.conversation.commands.create_group_chat;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateGroupChatCommand {
    UUID currentUserId;
    String name;
    String avatarUrl;
    Set<UUID> memberIds;
}
