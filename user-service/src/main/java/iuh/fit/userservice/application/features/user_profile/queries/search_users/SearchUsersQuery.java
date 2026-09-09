package iuh.fit.userservice.application.features.user_profile.queries.search_users;

import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SearchUsersQuery {
    BaseFilter filter;
}
