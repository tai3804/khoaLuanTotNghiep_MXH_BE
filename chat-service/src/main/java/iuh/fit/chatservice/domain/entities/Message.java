package iuh.fit.chatservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import iuh.fit.chatservice.domain.enums.MessageType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_message_conv", columnList = "conversation_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Message extends BaseEntity {

    @NotNull(message = "{message.conversationId.required}")
    @Column(name = "conversation_id", nullable = false)
    UUID conversationId;

    @Column(name = "sender_id")
    UUID senderId;

    @NotNull(message = "{message.type.required}")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    MessageType type = MessageType.TEXT;

    @Column(columnDefinition = "TEXT")
    String content;

    @Column(name = "media_url", length = 500)
    String mediaUrl;

    @Column(name = "reply_to_message_id")
    UUID replyToMessageId;

    @Column(name = "is_edited", nullable = false)
    @Builder.Default
    boolean edited = false;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    boolean deleted = false;

    @Column(name = "is_pinned", nullable = false)
    @Builder.Default
    boolean pinned = false;

    @Column(name = "pinned_at")
    Instant pinnedAt;

    @Column(name = "pinned_by_id")
    UUID pinnedById;
}
