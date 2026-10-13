package iuh.fit.mediaservice.application.features.media.commands.upload_multiple_media;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UploadMultipleMediaCommand {
    UUID userId;
    List<MultipartFile> files;
    String folder;
}
