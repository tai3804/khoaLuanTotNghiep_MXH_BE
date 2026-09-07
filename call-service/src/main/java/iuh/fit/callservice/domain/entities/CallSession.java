package iuh.fit.callservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import iuh.fit.callservice.domain.enums.CallStatus;
import iuh.fit.callservice.domain.enums.ChannelType;
import iuh.fit.callservice.domain.enums.MediaType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "call_sessions", indexes = {
        @Index(name = "idx_call_session_host", columnList = "host_user_id"),
        @Index(name = "idx_call_session_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CallSession extends BaseEntity {

    @NotNull(message = "{callSession.channelType.required}")
    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 20)
    ChannelType channelType;

    @NotNull(message = "{callSession.mediaType.required}")
    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    MediaType mediaType;

    @Column(name = "conversation_id")
    UUID conversationId;

    @NotNull(message = "{callSession.hostUserId.required}")
    @Column(name = "host_user_id", nullable = false)
    UUID hostUserId;

    @NotNull(message = "{callSession.status.required}")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    CallStatus status = CallStatus.INITIATED;

    @Column(name = "started_at")
    LocalDateTime startedAt;

    @Column(name = "ended_at")
    LocalDateTime endedAt;

    @Column(name = "duration_in_seconds", nullable = false)
    @Builder.Default
    long durationInSeconds = 0;
}
