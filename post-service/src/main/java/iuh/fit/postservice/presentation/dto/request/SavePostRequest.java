package iuh.fit.postservice.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SavePostRequest {

    @NotNull(message = "Post ID is required")
    UUID postId;

    String collectionName;
}
