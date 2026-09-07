package iuh.fit.chatservice.application.features.conversation.commands.create_direct_chat;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateDirectChatCommand {
    UUID currentUserId;
    UUID targetUserId;
}
