package iuh.fit.chatservice.application.features.conversation.queries.get_conversation_detail;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetConversationDetailQuery {
    UUID conversationId;
    UUID currentUserId;
}
