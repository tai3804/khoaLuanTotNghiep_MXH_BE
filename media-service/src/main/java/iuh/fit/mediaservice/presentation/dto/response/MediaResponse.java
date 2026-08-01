package iuh.fit.mediaservice.presentation.dto.response;

import iuh.fit.mediaservice.domain.enums.MediaType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MediaResponse {
    String fileUrl;
    String fileKey;
    String fileName;
    MediaType mediaType;
    long fileSize;
    String contentType;
}
