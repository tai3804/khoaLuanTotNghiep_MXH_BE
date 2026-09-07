package iuh.fit.callservice.presentation.dto.response;

import iuh.fit.callservice.domain.enums.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CallHistoryResponse {
    UUID callSessionId;
    ChannelType channelType;
    MediaType mediaType;
    UUID conversationId;
    UUID hostUserId;
    CallStatus status;
    LocalDateTime startedAt;
    LocalDateTime endedAt;
    long durationInSeconds;
    ParticipantRole myRole;
    ParticipantStatus myStatus;
}
