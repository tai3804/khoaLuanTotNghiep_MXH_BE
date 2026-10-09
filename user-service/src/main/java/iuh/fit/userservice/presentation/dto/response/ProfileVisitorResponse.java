package iuh.fit.userservice.presentation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProfileVisitorResponse {
    UUID viewerId;
    String firstName;
    String lastName;
    String middleName;
    String fullName;
    String avatarUrl;
    LocalDateTime lastViewedAt;
    boolean isFriend;
    boolean isFollowing;
}
