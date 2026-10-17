package iuh.fit.postservice.presentation.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MarkPostsSeenRequest {
    @NotEmpty(message = "postIds must not be empty")
    @Size(max = 100, message = "At most 100 posts per request")
    List<UUID> postIds;
}
