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
    /** Điểm phù hợp (càng cao càng nên gợi ý) và lý do gợi ý để hiển thị cho người dùng, vd "3 bạn chung". */
    double score;
    java.util.List<String> reasons;
}
