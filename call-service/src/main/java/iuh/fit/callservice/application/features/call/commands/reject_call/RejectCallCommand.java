package iuh.fit.callservice.application.features.call.commands.reject_call;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RejectCallCommand {
    UUID callSessionId;
    UUID currentUserId;
}
