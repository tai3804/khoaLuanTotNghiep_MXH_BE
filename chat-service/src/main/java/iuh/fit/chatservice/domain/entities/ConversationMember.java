package iuh.fit.chatservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import iuh.fit.chatservice.domain.enums.MemberRole;
import iuh.fit.chatservice.domain.enums.MemberStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "conversation_members", indexes = {
        @Index(name = "idx_conv_member_user", columnList = "user_id"),
        @Index(name = "idx_conv_member_conv", columnList = "conversation_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConversationMember extends BaseEntity {

    @NotNull(message = "{conversationMember.conversationId.required}")
    @Column(name = "conversation_id", nullable = false)
    UUID conversationId;

    @NotNull(message = "{conversationMember.userId.required}")
    @Column(name = "user_id", nullable = false)
    UUID userId;

    @NotNull(message = "{conversationMember.role.required}")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    MemberRole role = MemberRole.MEMBER;

    @Size(max = 50, message = "{conversationMember.nickname.size}")
    @Column(length = 50)
    String nickname;

    @NotNull(message = "{conversationMember.status.required}")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    MemberStatus status = MemberStatus.ACTIVE;

    @Column(name = "last_read_message_id")
    UUID lastReadMessageId;

    @Column(name = "joined_at", nullable = false)
    @Builder.Default
    LocalDateTime joinedAt = LocalDateTime.now();
}
