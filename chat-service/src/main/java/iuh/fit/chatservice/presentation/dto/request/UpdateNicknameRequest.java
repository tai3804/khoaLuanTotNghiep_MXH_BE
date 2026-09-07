package iuh.fit.chatservice.presentation.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateNicknameRequest {
    @Size(max = 50, message = "{conversationMember.nickname.size}")
    String nickname;
}
