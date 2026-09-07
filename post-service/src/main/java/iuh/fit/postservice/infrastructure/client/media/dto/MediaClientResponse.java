package iuh.fit.postservice.infrastructure.client.media.dto;

import iuh.fit.postservice.domain.enums.MediaType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MediaClientResponse {
    String fileUrl;
    String fileKey;
    String fileName;
    MediaType mediaType;
    long fileSize;
    String contentType;
}
