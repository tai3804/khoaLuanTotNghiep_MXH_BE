package iuh.fit.callservice.application.features.call.commands.toggle_media;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ToggleMediaCommand {
    UUID callSessionId;
    UUID currentUserId;
    Boolean audioMuted;
    Boolean videoMuted;
}
