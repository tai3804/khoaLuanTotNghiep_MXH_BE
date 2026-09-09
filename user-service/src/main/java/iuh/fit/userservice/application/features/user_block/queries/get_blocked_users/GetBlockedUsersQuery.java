package iuh.fit.userservice.application.features.user_block.queries.get_blocked_users;

import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetBlockedUsersQuery {
    UUID blockerId;
    BaseFilter filter;
}
