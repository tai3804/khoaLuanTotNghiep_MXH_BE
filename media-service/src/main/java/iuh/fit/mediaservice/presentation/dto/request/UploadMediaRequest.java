package iuh.fit.mediaservice.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UploadMediaRequest {

    @NotNull(message = "{media.file.required}")
    MultipartFile file;

    String folder;
}
