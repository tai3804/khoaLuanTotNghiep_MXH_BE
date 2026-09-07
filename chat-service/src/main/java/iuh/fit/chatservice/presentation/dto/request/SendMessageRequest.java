package iuh.fit.chatservice.presentation.dto.request;

import iuh.fit.chatservice.domain.enums.MessageType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SendMessageRequest {
    MessageType type;
    String content;
    String mediaUrl;
    UUID replyToMessageId;
}
