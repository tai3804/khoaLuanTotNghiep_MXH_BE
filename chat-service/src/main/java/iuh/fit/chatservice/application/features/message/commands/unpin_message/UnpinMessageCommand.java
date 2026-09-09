package iuh.fit.chatservice.application.features.message.commands.unpin_message;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UnpinMessageCommand {
    UUID conversationId;
    UUID messageId;
    UUID userId;
}
