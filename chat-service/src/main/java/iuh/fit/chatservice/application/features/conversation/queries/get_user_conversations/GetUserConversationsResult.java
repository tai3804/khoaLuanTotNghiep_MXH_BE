package iuh.fit.chatservice.application.features.conversation.queries.get_user_conversations;

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
public class GetUserConversationsResult {
    UUID conversationId;
    ConversationType type;
    String name;
    String avatarUrl;
    String lastMessageContent;
    LocalDateTime lastMessageAt;
    long unreadCount;
    UUID otherParticipantId;
}
