package iuh.fit.mediaservice.presentation.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UploadMultipleMediaRequest {

    @NotNull(message = "{media.files.required}")
    @NotEmpty(message = "{media.files.required}")
    List<MultipartFile> files;

    String folder;
}
