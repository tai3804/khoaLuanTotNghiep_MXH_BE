package iuh.fit.chatservice.application.features.message.commands.recall_message;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RecallMessageCommand {
    UUID conversationId;
    UUID messageId;
    UUID currentUserId;
}
