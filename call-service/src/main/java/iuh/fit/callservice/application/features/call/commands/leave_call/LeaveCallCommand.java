package iuh.fit.callservice.application.features.call.commands.leave_call;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LeaveCallCommand {
    UUID callSessionId;
    UUID currentUserId;
}
