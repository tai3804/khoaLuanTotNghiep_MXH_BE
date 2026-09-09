package iuh.fit.chatservice.application.features.message.queries.get_messages;

import iuh.fit.chatservice.domain.enums.MessageType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetMessagesResult {
    UUID messageId;
    UUID conversationId;
    UUID senderId;
    MessageType type;
    String content;
    String mediaUrl;
    UUID replyToMessageId;
    String replyToContent;
    UUID replyToSenderId;
    boolean edited;
    boolean deleted;
    boolean pinned;
    Instant pinnedAt;
    UUID pinnedById;
    Map<String, Long> reactionsCount;
    String currentUserReaction;
    LocalDateTime createdAt;
}
