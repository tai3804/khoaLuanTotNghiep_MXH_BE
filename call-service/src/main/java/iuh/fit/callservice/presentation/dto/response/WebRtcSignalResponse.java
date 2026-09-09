package iuh.fit.callservice.presentation.dto.response;

import iuh.fit.callservice.domain.enums.CallMediaType;
import iuh.fit.callservice.domain.enums.WebRtcSignalType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WebRtcSignalResponse {
    UUID callSessionId;
    UUID senderId;
    UUID targetUserId;
    WebRtcSignalType signalType;
    CallMediaType mediaType;
    Object sdp;
    Object candidate;
    Boolean audioMuted;
    Boolean videoMuted;
    Instant timestamp;
}
