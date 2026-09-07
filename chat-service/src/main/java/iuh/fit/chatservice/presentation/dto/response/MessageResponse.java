package iuh.fit.chatservice.presentation.dto.response;

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
public class MessageResponse {
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
