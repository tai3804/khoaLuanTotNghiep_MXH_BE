package iuh.fit.chatservice.application.features.member.commands.promote_admin;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PromoteAdminCommand {
    UUID conversationId;
    UUID currentUserId;
    UUID targetUserId;
}
