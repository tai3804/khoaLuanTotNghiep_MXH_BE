package iuh.fit.callservice.presentation.dto.request;

import iuh.fit.callservice.domain.enums.WebRtcSignalType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WebRtcSignalRequest {
    UUID callSessionId;
    UUID senderId;
    UUID targetUserId;
    WebRtcSignalType signalType;
    Object sdp;
    Object candidate;
    Boolean audioMuted;
    Boolean videoMuted;
}
