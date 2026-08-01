package iuh.fit.mediaservice.application.features.media.commands.upload_media;

import iuh.fit.mediaservice.domain.enums.MediaType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UploadMediaResult {
    String fileUrl;
    String fileKey;
    String fileName;
    MediaType mediaType;
    long fileSize;
    String contentType;
}
