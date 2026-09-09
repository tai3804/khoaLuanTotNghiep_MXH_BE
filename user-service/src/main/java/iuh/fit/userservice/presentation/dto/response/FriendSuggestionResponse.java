package iuh.fit.userservice.presentation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FriendSuggestionResponse {
    UUID userId;
    String username;
    String fullName;
    String avatarUrl;
    long mutualFriendsCount;
}
