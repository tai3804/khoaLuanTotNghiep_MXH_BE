package iuh.fit.chatservice.application.features.member.commands.demote_admin;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DemoteAdminCommand {
    UUID conversationId;
    UUID currentUserId;
    UUID targetUserId;
}
