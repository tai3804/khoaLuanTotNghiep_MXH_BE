package iuh.fit.chatservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import iuh.fit.chatservice.domain.enums.ConversationType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Conversation extends BaseEntity {

    @NotNull(message = "{conversation.type.required}")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    ConversationType type;

    @Size(max = 100, message = "{conversation.name.size}")
    @Column(length = 100)
    String name;

    @Size(max = 500, message = "{conversation.avatarUrl.size}")
    @Column(name = "avatar_url", length = 500)
    String avatarUrl;

    @Column(name = "creator_id")
    UUID creatorId;

    @Column(name = "max_members", nullable = false)
    @Builder.Default
    int maxMembers = 250;

    @Column(name = "last_message_content", columnDefinition = "TEXT")
    String lastMessageContent;

    @Column(name = "last_message_at")
    LocalDateTime lastMessageAt;
}
