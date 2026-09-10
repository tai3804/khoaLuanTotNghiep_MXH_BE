package iuh.fit.mediaservice.presentation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PresignedUrlResponse {
    String presignedUrl;
    String fileKey;
    String fileUrl;
    int expiresInMinutes;
}
