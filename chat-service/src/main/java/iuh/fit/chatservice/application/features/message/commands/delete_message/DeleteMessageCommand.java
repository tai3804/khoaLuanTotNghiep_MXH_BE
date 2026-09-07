package iuh.fit.chatservice.application.features.message.commands.delete_message;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeleteMessageCommand {
    UUID messageId;
    UUID currentUserId;
}
