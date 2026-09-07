package iuh.fit.callservice.presentation.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WebRtcSignalRequest {
    UUID targetUserId;
    String signalType; // "offer", "answer", "candidate"
    Object sdp;
    Object candidate;
}
