package iuh.fit.chatservice.application.features.conversation.queries.get_user_conversations;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetUserConversationsQuery {
    UUID currentUserId;
    int page;
    int size;
}
