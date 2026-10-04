package iuh.fit.chatservice.presentation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RecallMessageResponse {
    UUID messageId;
    UUID conversationId;
    boolean recalled;
    String message;
}
