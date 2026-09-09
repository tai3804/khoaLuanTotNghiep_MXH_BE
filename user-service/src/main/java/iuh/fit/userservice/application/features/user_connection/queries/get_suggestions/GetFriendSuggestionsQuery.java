package iuh.fit.userservice.application.features.user_connection.queries.get_suggestions;

import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetFriendSuggestionsQuery {
    UUID currentUserId;
    BaseFilter filter;
}
