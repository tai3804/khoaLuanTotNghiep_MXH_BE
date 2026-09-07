package iuh.fit.callservice.application.features.call.queries.get_call_history;

import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetCallHistoryQuery {
    UUID currentUserId;
    BaseFilter filter;
}
