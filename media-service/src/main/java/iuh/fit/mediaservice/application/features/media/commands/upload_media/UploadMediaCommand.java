package iuh.fit.mediaservice.application.features.media.commands.upload_media;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UploadMediaCommand {
    UUID userId;
    MultipartFile file;
    String folder;
}
