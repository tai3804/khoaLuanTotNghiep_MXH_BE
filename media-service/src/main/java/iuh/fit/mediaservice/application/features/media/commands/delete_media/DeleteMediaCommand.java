package iuh.fit.mediaservice.application.features.media.commands.delete_media;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeleteMediaCommand {
    String fileKey;
}
