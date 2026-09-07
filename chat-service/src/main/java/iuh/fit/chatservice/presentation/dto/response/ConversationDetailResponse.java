package iuh.fit.chatservice.presentation.dto.response;

import iuh.fit.chatservice.domain.enums.ConversationType;
import iuh.fit.chatservice.domain.enums.MemberRole;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConversationDetailResponse {
    UUID conversationId;
    ConversationType type;
    String name;
    String avatarUrl;
    UUID creatorId;
    int maxMembers;
    String lastMessageContent;
    LocalDateTime lastMessageAt;
    List<MemberResponse> members;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class MemberResponse {
        UUID userId;
        MemberRole role;
        String nickname;
        LocalDateTime joinedAt;
    }
}
