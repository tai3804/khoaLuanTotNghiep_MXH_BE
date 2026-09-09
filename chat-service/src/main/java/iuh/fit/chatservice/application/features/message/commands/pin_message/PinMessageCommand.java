package iuh.fit.chatservice.application.features.message.commands.pin_message;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PinMessageCommand {
    UUID conversationId;
    UUID messageId;
    UUID userId;
}
