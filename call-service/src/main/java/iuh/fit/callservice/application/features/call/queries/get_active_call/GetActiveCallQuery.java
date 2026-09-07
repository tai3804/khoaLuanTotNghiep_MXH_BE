package iuh.fit.callservice.application.features.call.queries.get_active_call;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetActiveCallQuery {
    UUID currentUserId;
}
