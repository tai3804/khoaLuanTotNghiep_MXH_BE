package iuh.fit.chatservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "message_reactions", indexes = {
        @Index(name = "idx_reaction_msg", columnList = "message_id"),
        @Index(name = "idx_reaction_msg_user", columnList = "message_id, user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MessageReaction extends BaseEntity {

    @NotNull(message = "Message ID is required")
    @Column(name = "message_id", nullable = false)
    UUID messageId;

    @NotNull(message = "Conversation ID is required")
    @Column(name = "conversation_id", nullable = false)
    UUID conversationId;

    @NotNull(message = "User ID is required")
    @Column(name = "user_id", nullable = false)
    UUID userId;

    @NotNull(message = "Emoji reaction is required")
    @Column(name = "emoji", nullable = false, length = 50)
    String emoji;
}
