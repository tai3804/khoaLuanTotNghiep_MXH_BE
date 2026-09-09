package iuh.fit.postservice.presentation.dto.request;

import iuh.fit.postservice.domain.enums.PostPrivacy;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateStoryRequest {

    @NotBlank(message = "Media URL is required")
    String mediaUrl;

    String mediaType; // IMAGE, VIDEO

    String caption;

    PostPrivacy privacy;
}
