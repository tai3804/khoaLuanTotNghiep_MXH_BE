package iuh.fit.chatservice.application.features.message.commands.send_message;

import iuh.fit.chatservice.domain.enums.MessageType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SendMessageResult {
    UUID messageId;
    UUID conversationId;
    UUID senderId;
    MessageType type;
    String content;
    String mediaUrl;
    UUID replyToMessageId;
    boolean edited;
    boolean deleted;
    LocalDateTime createdAt;
}
