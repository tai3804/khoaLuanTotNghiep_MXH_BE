package iuh.fit.postservice.application.features.post.commands.create_post;

import iuh.fit.postservice.domain.enums.PostPrivacy;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreatePostCommand {
    UUID authorId;
    String content;
    PostPrivacy privacy;
    Set<UUID> allowedUserIds;
    List<MultipartFile> files;
}
