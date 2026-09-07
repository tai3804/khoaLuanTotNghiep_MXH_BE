package iuh.fit.chatservice.application.features.conversation.queries.get_conversation_detail;

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
public class GetConversationDetailResult {
    UUID conversationId;
    ConversationType type;
    String name;
    String avatarUrl;
    UUID creatorId;
    int maxMembers;
    String lastMessageContent;
    LocalDateTime lastMessageAt;
    List<MemberResult> members;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class MemberResult {
        UUID userId;
        MemberRole role;
        String nickname;
        LocalDateTime joinedAt;
    }
}
