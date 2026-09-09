package iuh.fit.chatservice.application.features.message.queries.search_messages;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SearchMessagesQuery {
    UUID conversationId;
    UUID currentUserId;
    String keyword;
    int page;
    int size;
}
