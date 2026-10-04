package iuh.fit.chatservice.application.features.message.commands.edit_message;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EditMessageCommand {
    UUID conversationId;
    UUID messageId;
    UUID currentUserId;
    String newContent;
}
