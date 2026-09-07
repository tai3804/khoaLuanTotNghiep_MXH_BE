package iuh.fit.chatservice.application.features.member.commands.add_members;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AddGroupMembersCommand {
    UUID conversationId;
    UUID currentUserId;
    Set<UUID> userIdsToAdd;
}
