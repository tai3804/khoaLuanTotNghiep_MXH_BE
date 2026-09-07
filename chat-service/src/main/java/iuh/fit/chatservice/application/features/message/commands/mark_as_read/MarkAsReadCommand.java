package iuh.fit.chatservice.application.features.message.commands.mark_as_read;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MarkAsReadCommand {
    UUID conversationId;
    UUID currentUserId;
    UUID messageId;
}
