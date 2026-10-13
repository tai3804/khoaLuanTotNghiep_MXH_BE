package iuh.fit.postservice.application.features.post.commands.apply_summary;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ApplyPostSummaryCommand {
    UUID postId;
    String summary;
    int contentLength;
}
