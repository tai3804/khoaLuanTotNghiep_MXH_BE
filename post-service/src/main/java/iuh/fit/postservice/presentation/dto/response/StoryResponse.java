package iuh.fit.postservice.presentation.dto.response;

import iuh.fit.postservice.domain.enums.PostPrivacy;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StoryResponse {
    UUID id;
    UUID userId;
    String mediaUrl;
    String mediaType;
    String caption;
    PostPrivacy privacy;
    long viewsCount;
    boolean isViewedByMe;
    Instant expiresAt;
    Instant createdAt;
}
