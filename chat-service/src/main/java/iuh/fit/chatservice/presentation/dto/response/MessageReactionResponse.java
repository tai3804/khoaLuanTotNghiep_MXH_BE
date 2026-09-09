package iuh.fit.chatservice.presentation.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MessageReactionResponse {
    UUID id;
    UUID messageId;
    UUID conversationId;
    UUID userId;
    String emoji;
    LocalDateTime createdAt;
}
