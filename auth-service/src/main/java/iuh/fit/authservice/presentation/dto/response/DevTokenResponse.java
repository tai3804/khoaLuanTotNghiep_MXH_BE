package iuh.fit.authservice.presentation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DevTokenResponse {
    String accessToken;
    @Builder.Default
    String tokenType = "Bearer";
    UUID userId;
    Instant expiresAt;
}
