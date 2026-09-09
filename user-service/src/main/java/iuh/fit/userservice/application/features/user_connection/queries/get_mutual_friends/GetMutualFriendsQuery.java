package iuh.fit.userservice.application.features.user_connection.queries.get_mutual_friends;

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
public class GetMutualFriendsQuery {
    UUID currentUserId;
    UUID targetUserId;
    BaseFilter filter;
}
