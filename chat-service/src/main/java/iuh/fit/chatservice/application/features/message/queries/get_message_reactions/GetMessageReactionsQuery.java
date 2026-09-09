package iuh.fit.chatservice.application.features.message.queries.get_message_reactions;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetMessageReactionsQuery {
    UUID conversationId;
    UUID messageId;
    UUID userId;
}
