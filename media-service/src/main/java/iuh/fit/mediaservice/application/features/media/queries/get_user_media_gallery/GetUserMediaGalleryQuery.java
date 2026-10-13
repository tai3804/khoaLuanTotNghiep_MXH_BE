package iuh.fit.mediaservice.application.features.media.queries.get_user_media_gallery;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetUserMediaGalleryQuery {
    String userId;
    int page;
    int size;
}
