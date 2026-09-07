package iuh.fit.chatservice.presentation.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateGroupChatRequest {
    @Size(max = 100, message = "{conversation.name.size}")
    String name;

    @Size(max = 500, message = "{conversation.avatarUrl.size}")
    String avatarUrl;

    Set<UUID> memberIds;
}
