package iuh.fit.moderationservice.application.features.moderation.queries.get_moderation_logs;

import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetModerationLogsQuery {
    BaseFilter filter;
}
