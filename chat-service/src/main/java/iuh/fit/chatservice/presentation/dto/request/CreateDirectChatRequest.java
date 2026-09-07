package iuh.fit.chatservice.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateDirectChatRequest {
    @NotNull(message = "{conversationMember.userId.required}")
    UUID targetUserId;
}
