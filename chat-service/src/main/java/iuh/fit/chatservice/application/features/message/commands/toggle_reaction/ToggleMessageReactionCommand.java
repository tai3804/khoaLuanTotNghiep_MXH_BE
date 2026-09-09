package iuh.fit.chatservice.application.features.message.commands.toggle_reaction;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ToggleMessageReactionCommand {
    UUID conversationId;
    UUID messageId;
    UUID userId;
    String emoji;
}
