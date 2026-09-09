package iuh.fit.chatservice.application.features.message.commands.delete_for_me;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeleteMessageForMeCommand {
    UUID conversationId;
    UUID messageId;
    UUID currentUserId;
}
