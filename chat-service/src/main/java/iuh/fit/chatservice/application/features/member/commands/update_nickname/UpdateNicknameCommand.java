package iuh.fit.chatservice.application.features.member.commands.update_nickname;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateNicknameCommand {
    UUID conversationId;
    UUID currentUserId;
    UUID targetUserId;
    String nickname;
}
