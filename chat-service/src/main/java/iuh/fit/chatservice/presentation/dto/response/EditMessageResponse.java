package iuh.fit.chatservice.presentation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EditMessageResponse {
    UUID messageId;
    UUID conversationId;
    String content;
    boolean edited;
    LocalDateTime updatedAt;
}
