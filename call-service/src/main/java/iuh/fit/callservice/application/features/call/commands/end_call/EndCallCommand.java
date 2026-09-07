package iuh.fit.callservice.application.features.call.commands.end_call;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EndCallCommand {
    UUID callSessionId;
    UUID currentUserId;
}
