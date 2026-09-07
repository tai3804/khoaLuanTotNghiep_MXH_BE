package iuh.fit.callservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import iuh.fit.callservice.domain.enums.ParticipantRole;
import iuh.fit.callservice.domain.enums.ParticipantStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "call_participants", indexes = {
        @Index(name = "idx_call_part_session", columnList = "call_session_id"),
        @Index(name = "idx_call_part_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CallParticipant extends BaseEntity {

    @NotNull(message = "{callParticipant.callSessionId.required}")
    @Column(name = "call_session_id", nullable = false)
    UUID callSessionId;

    @NotNull(message = "{callParticipant.userId.required}")
    @Column(name = "user_id", nullable = false)
    UUID userId;

    @NotNull(message = "{callParticipant.role.required}")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    ParticipantRole role = ParticipantRole.PARTICIPANT;

    @NotNull(message = "{callParticipant.status.required}")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    ParticipantStatus status = ParticipantStatus.INVITED;

    @Column(name = "audio_muted", nullable = false)
    @Builder.Default
    boolean audioMuted = false;

    @Column(name = "video_muted", nullable = false)
    @Builder.Default
    boolean videoMuted = false;

    @Column(name = "joined_at")
    LocalDateTime joinedAt;

    @Column(name = "left_at")
    LocalDateTime leftAt;
}
