package iuh.fit.chatservice.presentation.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AddGroupMembersRequest {
    @NotEmpty(message = "{conversationMember.userId.required}")
    Set<UUID> userIdsToAdd;
}
