package iuh.fit.chatservice.application.features.member.commands.leave_group;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LeaveGroupCommand {
    UUID conversationId;
    UUID currentUserId;
}
