package iuh.fit.postservice.presentation.dto.response;

import iuh.fit.postservice.domain.enums.ReactionType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReactionResponse {
    UUID id;
    UUID postId;
    UUID userId;
    ReactionType type;
    LocalDateTime createdAt;
}
