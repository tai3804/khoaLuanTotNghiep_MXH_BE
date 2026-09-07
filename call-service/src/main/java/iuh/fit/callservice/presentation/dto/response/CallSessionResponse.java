package iuh.fit.callservice.presentation.dto.response;

import iuh.fit.callservice.domain.enums.*;
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
public class CallSessionResponse {
    UUID callSessionId;
    ChannelType channelType;
    MediaType mediaType;
    UUID conversationId;
    UUID hostUserId;
    CallStatus status;
    LocalDateTime startedAt;
    List<ParticipantResponse> participants;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ParticipantResponse {
        UUID userId;
        ParticipantRole role;
        ParticipantStatus status;
        boolean audioMuted;
        boolean videoMuted;
    }
}
