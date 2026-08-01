package iuh.fit.mediaservice.application.features.media.commands.upload_media;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UploadMediaCommand {
    MultipartFile file;
    String folder;
}
