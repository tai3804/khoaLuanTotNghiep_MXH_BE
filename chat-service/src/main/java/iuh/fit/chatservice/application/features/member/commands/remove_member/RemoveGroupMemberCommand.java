package iuh.fit.chatservice.application.features.member.commands.remove_member;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RemoveGroupMemberCommand {
    UUID conversationId;
    UUID currentUserId;
    UUID targetUserId;
}
