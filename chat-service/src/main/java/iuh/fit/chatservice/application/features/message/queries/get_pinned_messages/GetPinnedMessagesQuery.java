package iuh.fit.chatservice.application.features.message.queries.get_pinned_messages;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetPinnedMessagesQuery {
    UUID conversationId;
    UUID userId;
}
