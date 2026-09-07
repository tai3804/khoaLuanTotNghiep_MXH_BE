package iuh.fit.postservice.presentation.dto.request;

import iuh.fit.postservice.domain.enums.PostPrivacy;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SharePostRequest {
    String caption;
    PostPrivacy privacy;
}
