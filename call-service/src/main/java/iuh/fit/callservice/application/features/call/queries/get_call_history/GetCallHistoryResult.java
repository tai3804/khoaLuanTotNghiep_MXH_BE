package iuh.fit.callservice.application.features.call.queries.get_call_history;

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
public class GetCallHistoryResult {
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
