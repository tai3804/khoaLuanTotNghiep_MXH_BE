package iuh.fit.postservice.application.features.reaction.queries.get_all_reactions;

import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetAllReactionsQuery {
    BaseFilter filter;
}
