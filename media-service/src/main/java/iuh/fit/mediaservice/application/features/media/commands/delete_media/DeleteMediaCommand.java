package iuh.fit.mediaservice.application.features.media.commands.delete_media;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeleteMediaCommand {
    String fileKey;
}
