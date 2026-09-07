package iuh.fit.chatservice.application.features.message.queries.get_messages;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetMessagesQuery {
    UUID conversationId;
    UUID currentUserId;
    int page;
    int size;
}
