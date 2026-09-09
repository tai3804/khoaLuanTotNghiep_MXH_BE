package iuh.fit.userservice.application.features.user_connection.queries.get_suggestions;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FriendSuggestionResult {
    UUID userId;
    String username;
    String fullName;
    String avatarUrl;
    long mutualFriendsCount;
}
