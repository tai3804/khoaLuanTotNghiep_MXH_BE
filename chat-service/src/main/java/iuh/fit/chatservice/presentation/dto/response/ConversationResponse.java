package iuh.fit.chatservice.presentation.dto.response;

import iuh.fit.chatservice.domain.enums.ConversationType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConversationResponse {
    UUID conversationId;
    ConversationType type;
    String name;
    String avatarUrl;
    String lastMessageContent;
    LocalDateTime lastMessageAt;
    long unreadCount;
    UUID otherParticipantId;
}
