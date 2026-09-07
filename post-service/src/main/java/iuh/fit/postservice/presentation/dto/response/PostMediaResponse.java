package iuh.fit.postservice.presentation.dto.response;

import iuh.fit.postservice.domain.enums.MediaType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostMediaResponse {
    UUID id;
    String fileUrl;
    String fileKey;
    MediaType mediaType;
    long fileSize;
    int sortOrder;
}
