package iuh.fit.postservice.application.features.story.commands.create_story;

import iuh.fit.postservice.domain.enums.PostPrivacy;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateStoryCommand {
    UUID userId;
    String mediaUrl;
    String mediaType;
    String caption;
    PostPrivacy privacy;
}
