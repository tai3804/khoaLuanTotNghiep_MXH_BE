package iuh.fit.mediaservice.application.features.media.queries.get_presigned_url;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetPresignedUrlQuery {
    String folder;
    String fileName;
    String contentType;
    int durationMinutes;
}
